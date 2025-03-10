/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.impliedlabels;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.LabelFinder;
import hudson.model.ManagementLink;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ConfigTest {

    private static final EnvVars NO_ENV = new EnvVars();

    private JenkinsRule j;

    private Config config;
    private List<Implication> implications;
    private String controllerLabel;

    @BeforeEach
    void setUp(JenkinsRule j) throws IOException {
        this.j = j;
        config = ImpliedLabelsPlugin.get().getConfig();
        implications = Arrays.asList(
                new Implication("rhel64 || rhel65", "rhel6"),
                new Implication("rhel4 || rhel5 || rhel6", "rhel"),
                new Implication("fedora17 || fedora18", "fedora"),
                new Implication("rhel || fedora", "linux"),
                new Implication("||", "invalid"));
        config.implications(implications);
        controllerLabel = Jenkins.get().getSelfLabel().getName();
    }

    @Test
    void roundtrip() {
        assertThat(config.implications(), sameMembers(implications));

        assertThat(new Config().implications(), sameMembers(implications));
    }

    @Test
    void evaluate() throws IOException {
        j.jenkins.setLabelString("rhel65");

        config.evaluate(j.jenkins);

        assertThat(j.jenkins.getLabelAtoms(), sameMembers(labels("rhel65", "rhel6", "rhel", "linux", controllerLabel)));
    }

    @Test
    void evaluateInAnyOrder() throws IOException {
        j.jenkins.setLabelString("rhel65");

        Collections.reverse(implications);
        config.implications(implications);
        config.evaluate(j.jenkins);

        assertThat(j.jenkins.getLabelAtoms(), sameMembers(labels("rhel65", "rhel6", "rhel", "linux", controllerLabel)));
    }

    @Test
    void considerLabelsContributedByOtherLabelFinders() throws IOException {
        j.jenkins.setLabelString("configured");
        config.implications(
                Collections.singletonList(new Implication("configured && contributed && " + controllerLabel, "final")));

        assertThat(j.jenkins.getLabels(), hasItem(label("final")));
    }

    @Extension // @TestExtension("considerLabelsContributedByOtherLabelFinders")
    public static class TestLabelFinder extends LabelFinder {
        @Override
        @NonNull
        public Collection<LabelAtom> findLabels(Node node) {
            // @TestExtension does not seem to work using JenkinsRule
            if (!node.getLabelString().contains("configured")) return Collections.emptyList();

            return labels("contributed");
        }
    }

    private static LabelAtom label(String label) {
        return Jenkins.get().getLabelAtom(label);
    }

    private static Set<LabelAtom> labels(String... names) {
        HashSet<LabelAtom> labels = new HashSet<>(names.length);
        for (String name : names) {
            labels.add(label(name));
        }
        return labels;
    }

    @Test
    void validateExpression() {
        assertThat(config.doCheckExpression(""), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression(controllerLabel), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression("!" + controllerLabel), equalTo(FormValidation.ok()));

        assertThat(config.doCheckExpression("!||&&").getMessage(), containsString("Invalid label expression"));
    }

    @Test
    void notAuthorizedToRead() {
        // Create a security realm that allows no read
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        try (WebClient wc = j.createWebClient()) {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            FailingHttpStatusCodeException ex =
                    assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("label-implications"));
            assertThat(ex.getStatusMessage(), equalTo("Forbidden"));
        }
    }

    @Test
    void notAuthorizedToConfigure() throws Exception {
        // Create a security realm that only allows a-read-only-user to read
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.READ).everywhere().to("a-read-only-user");
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        try (WebClient wc = j.createWebClient()) {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);

            // Login as the user that can only read
            wc.login("a-read-only-user");

            FailingHttpStatusCodeException ex1 =
                    assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("label-implications"));
            assertThat(ex1.getStatusMessage(), equalTo("Forbidden"));

            FailingHttpStatusCodeException ex2 =
                    assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("label-implications/configure"));
            assertThat(ex2.getStatusMessage(), equalTo("Forbidden"));
        }
    }

    @Test
    void detectRedundant() throws IOException {
        j.jenkins.setLabelString("rhel65");
        assertThat(config.detectRedundantLabels(j.jenkins), sameMembers());

        j.jenkins.setLabelString("rhel65 linux");
        assertThat(config.detectRedundantLabels(j.jenkins), sameMembers(label("linux")));

        j.jenkins.setLabelString("rhel65 linux");
        assertThat(config.detectRedundantLabels(j.jenkins), sameMembers(label("linux")));
    }

    private static <T> TypeSafeMatcher<Collection<T>> sameMembers(Collection<T> items) {
        return new SameMembers<>(items);
    }

    @SafeVarargs
    private static <T> TypeSafeMatcher<Collection<T>> sameMembers(T... items) {
        return new SameMembers<>(Arrays.asList(items));
    }

    private static class SameMembers<T> extends TypeSafeMatcher<Collection<T>> {

        private final Set<T> items;

        public SameMembers(Collection<T> items) {
            this.items = new HashSet<>(items);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Implies: " + items);
        }

        @Override
        protected boolean matchesSafely(Collection<T> item) {
            return items.equals(new HashSet<>(item));
        }
    }

    @Test
    void cacheImpliedLabels() throws Exception {
        TrackingImplication tracker = new TrackingImplication();
        ArrayList<Implication> impls = new ArrayList<>(implications);
        impls.add(tracker);
        implications = impls;
        config.implications(impls);

        DumbSlave f1 = j.createSlave("f1", "fedora17", NO_ENV);
        DumbSlave f2 = j.createSlave("f2", "fedora17", NO_ENV);
        DumbSlave r6 = j.createSlave("r6", "rhel6", NO_ENV);
        DumbSlave r6x = j.createSlave("r6x", "rhel6 something_extra", NO_ENV);

        for (int i = 0; i < 3; i++) {
            assertThat(config.evaluate(f1), sameMembers(labels("fedora17", "fedora", "linux", "f1")));
            assertThat(config.evaluate(f2), sameMembers(labels("fedora17", "fedora", "linux", "f2")));
            assertThat(config.evaluate(r6), sameMembers(labels("rhel6", "rhel", "linux", "r6")));
            assertThat(config.evaluate(r6x), sameMembers(labels("rhel6", "rhel", "linux", "something_extra", "r6x")));
        }

        config.implications(impls);
        tracker.clear();

        for (int i = 0; i < 3; i++) {
            assertThat(config.evaluate(f1), sameMembers(labels("fedora17", "fedora", "linux", "f1")));
            assertThat(config.evaluate(f2), sameMembers(labels("fedora17", "fedora", "linux", "f2")));
            assertThat(config.evaluate(r6), sameMembers(labels("rhel6", "rhel", "linux", "r6")));
            assertThat(config.evaluate(r6x), sameMembers(labels("rhel6", "rhel", "linux", "something_extra", "r6x")));
        }
    }

    @Test
    void testManagementCategory() {
        assertThat(config.getCategory(), is(ManagementLink.Category.CONFIGURATION));
    }

    @Test
    void testDescription() {
        assertThat(config.getDescription(), is("Infer redundant labels automatically based on user declaration"));
    }

    @Test
    void testIconFileName() {
        assertThat(config.getIconFileName(), is("symbol-pricetags-outline plugin-ionicons-api"));
    }

    @Test
    void testAutoCompleteLabels() {
        /* Prefix substring of controller label should autocomplete to full controller label */
        String controllerLabelPrefix = controllerLabel.substring(0, 4);
        AutoCompletionCandidates candidates = config.doAutoCompleteLabels(controllerLabelPrefix);
        assertThat(candidates.getValues(), hasItem(controllerLabel));
    }

    @Test
    void testAutoCompleteLabels_Invalid() {
        /* Invalid prefix should not autocomplete */
        String invalidPrefix = "invalid-prefix-for-auto-complete";
        AutoCompletionCandidates candidates = config.doAutoCompleteLabels(invalidPrefix);
        assertThat(candidates.getValues(), is(empty()));
    }

    @Test
    void testAutoCompleteLabels_Implication() {
        /* Implication should autocomplete */
        String impliedLabelPrefix = "fed";
        AutoCompletionCandidates candidates = config.doAutoCompleteLabels(impliedLabelPrefix);
        assertThat(candidates.getValues(), hasItem("fedora"));
    }

    private static final class TrackingImplication extends Implication {
        private final Map<Collection<LabelAtom>, Throwable> log = new HashMap<>();

        public TrackingImplication() {
            super("", "");
        }

        @NonNull
        @Override // Infer nothing
        public Collection<LabelAtom> infer(@NonNull Collection<LabelAtom> atoms) {
            synchronized (log) {
                Throwable where = log.get(atoms);
                if (where != null) {
                    AssertionError ae = new AssertionError("Duplicate label lookup for " + atoms);
                    ae.addSuppressed(where);
                    throw ae;
                }
                assertNull(log.put(atoms, new Exception()));
            }
            return Collections.emptyList();
        }

        public void clear() {
            synchronized (log) {
                log.clear();
            }
        }
    }
}

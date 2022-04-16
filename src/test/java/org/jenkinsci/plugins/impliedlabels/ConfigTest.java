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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import hudson.EnvVars;
import hudson.Extension;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import edu.umd.cs.findbugs.annotations.NonNull;

public class ConfigTest {

    private static final EnvVars NO_ENV = new EnvVars();

    @Rule public JenkinsRule j = new JenkinsRule();

    private Config config;
    private List<Implication> implications;
    private String controllerLabel;

    @Before public void setUp() throws IOException {
        config = ImpliedLabelsPlugin.config;
        implications = Arrays.asList(
                new Implication("rhel64 || rhel65", "rhel6"),
                new Implication("rhel4 || rhel5 || rhel6", "rhel"),
                new Implication("fedora17 || fedora18", "fedora"),
                new Implication("rhel || fedora", "linux"),
                new Implication("||", "invalid")
        );
        config.implications(implications);
        controllerLabel = j.jenkins.get().getSelfLabel().getName();
    }

    @Test public void roundtrip() {
        assertThat(config.implications(), sameMembers(implications));

        assertThat(new Config().implications(), sameMembers(implications));
    }

    @Test public void evaluate() throws IOException {
        j.jenkins.setLabelString("rhel65");

        config.evaluate(j.jenkins);

        assertThat(j.jenkins.getLabelAtoms(), sameMembers(labels("rhel65", "rhel6", "rhel", "linux", controllerLabel)));
    }

    @Test public void evaluateInAnyOrder() throws IOException {
        j.jenkins.setLabelString("rhel65");

        Collections.reverse(implications);
        config.implications(implications);
        config.evaluate(j.jenkins);

        assertThat(j.jenkins.getLabelAtoms(), sameMembers(labels("rhel65", "rhel6", "rhel", "linux", controllerLabel)));
    }

    @Test public void considerLabelsContributedByOtherLabelFinders() throws IOException {
        j.jenkins.setLabelString("configured");
        config.implications(Collections.singletonList(
                new Implication("configured && contributed && " + controllerLabel, "final")
        ));

        assertThat(j.jenkins.getLabels(), hasItem(label("final")));
    }

    @Extension//@TestExtension("considerLabelsContributedByOtherLabelFinders")
    public static class TestLabelFinder extends LabelFinder {
        @Override public Collection<LabelAtom> findLabels(Node node) {
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
        for (String name: names) {
            labels.add(label(name));
        }
        return labels;
    }

    @Test public void validateExpression() {
        assertThat(config.doCheckExpression(""), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression(controllerLabel), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression("!" + controllerLabel), equalTo(FormValidation.ok()));

        assertThat(config.doCheckExpression("!||&&").getMessage(), containsString("Invalid label expression"));
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @Test public void notAuthorizedToRead() throws Exception {
        WebClient wc = j.createWebClient();

        try {
            wc.getOptions().setPrintContentOnFailingStatusCode(false);
            wc.goTo("label-implications");
        } catch (FailingHttpStatusCodeException ex) {
            assertThat(ex.getStatusMessage(), equalTo("Forbidden"));
        }
    }

    @PresetData(DataSet.ANONYMOUS_READONLY)
    @Test public void notAuthorizedToConfigure() throws Exception {
        WebClient wc = j.createWebClient();
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        try {
            wc.goTo("label-implications");
        } catch (FailingHttpStatusCodeException ex) {
            assertThat(ex.getStatusMessage(), equalTo("Forbidden"));
        }

        try {
            wc.goTo("label-implications/configure");
        } catch (FailingHttpStatusCodeException ex) {
            assertThat(ex.getStatusMessage(), equalTo("Forbidden"));
        }
    }

    @Test public void detectRedundant() throws IOException {
        j.jenkins.setLabelString("rhel65");
        assertThat(config.detectRedundantLabels(j.jenkins), this.sameMembers());

        j.jenkins.setLabelString("rhel65 linux");
        assertThat(config.detectRedundantLabels(j.jenkins), this.sameMembers(label("linux")));

        j.jenkins.setLabelString("rhel65 linux");
        assertThat(config.detectRedundantLabels(j.jenkins), this.sameMembers(label("linux")));
    }

    private <T> TypeSafeMatcher<Collection<T>> sameMembers(Collection<T> items) {
        return new SameMembers<>(items);
    }

    private <T> TypeSafeMatcher<Collection<T>> sameMembers(T... items) {
        return new SameMembers<>(Arrays.asList(items));
    }

    private static class SameMembers<T> extends TypeSafeMatcher<Collection<T>> {

        private final Set<T> items;

        public SameMembers(Collection<T> items) {
            this.items = new HashSet<>(items);
        }

        public void describeTo(Description description) {
            description.appendText("Implies: " + items);
        }

        @Override
        protected boolean matchesSafely(Collection<T> item) {
            return items.equals(new HashSet<>(item));
        }
    }

    @Test public void cacheImpliedLabels() throws Exception {
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

    @Test public void testManagementCategory() {
        assertThat(config.getCategory(), is(ManagementLink.Category.CONFIGURATION));
    }

    @Test public void testDescription() {
        assertThat(config.getDescription(), is("Infer redundant labels automatically based on user declaration"));
    }

    @Test public void testIconFileName() {
        assertThat(config.getIconFileName(), is("/plugin/implied-labels/icons/48x48/attribute.png"));
    }

    private static final class TrackingImplication extends Implication {
        private final Map<Collection<LabelAtom>, Throwable> log = new HashMap<>();

        public TrackingImplication() {
            super("", "");
        }

        @NonNull @Override // Infer nothing
        public Collection<LabelAtom> infer(@NonNull Collection<LabelAtom> atoms) {
            synchronized (log) {
                Throwable where = log.get(atoms);
                if (where != null) {
                    AssertionError ae = new AssertionError("Duplicate label lookup for " + atoms);
                    ae.addSuppressed(where);
                    throw ae;
                }
                assertEquals(null, log.put(atoms, new Exception()));
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

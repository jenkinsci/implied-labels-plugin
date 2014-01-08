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
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

public class ConfigTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private Config config;
    private List<Implication> implications;

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
    }

    @Test public void roundtrip() {
        assertThat(config.implications(), sameMembers(implications));

        assertThat(new Config().implications(), sameMembers(implications));
    }

    @Test public void evaluate() throws IOException {
        j.jenkins.setLabelString("rhel65");

        config.evaluate(j.jenkins);

        HashSet<Label> expected = new HashSet<Label>(Arrays.asList(
                label("rhel65"), label("rhel6"), label("rhel"), label("linux"), label("master")
        ));

        assertThat(j.jenkins.getLabels(), sameMembers(expected));
    }

    @Test public void evaluateInAnyOrder() throws IOException {
        j.jenkins.setLabelString("rhel65");

        Collections.reverse(implications);
        config.implications(implications);
        config.evaluate(j.jenkins);

        HashSet<Label> expected = new HashSet<Label>(Arrays.asList(
                label("rhel65"), label("rhel6"), label("rhel"), label("linux"), label("master")
        ));

        assertThat(j.jenkins.getLabels(), sameMembers(expected));
    }

    private Label label(String label) {
        return j.jenkins.getLabelAtom(label);
    }

    @Test public void validateExpression() {
        assertThat(config.doCheckExpression(""), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression("master"), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression("!master"), equalTo(FormValidation.ok()));

        assertThat(config.doCheckExpression("!||&&").getMessage(), containsString("Invalid boolean expression"));
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @Test public void notAuthorizedToRead() throws Exception {
        WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);

        String content = wc.goTo("label-implications").asText(); // Redirected to login
        assertThat(content, containsString("Password:"));
        assertThat(content, not(containsString(config.getDisplayName())));
    }

    @PresetData(DataSet.ANONYMOUS_READONLY)
    @Test public void notAuthorizedToConfigure() throws Exception {
        WebClient wc = j.createWebClient();

        String content = wc.goTo("label-implications").asText();
        assertThat(content, containsString(config.getDisplayName()));
        assertThat(content, not(containsString("Password:")));

        wc.setThrowExceptionOnFailingStatusCode(false);

        content = wc.goTo("label-implications/configure").asText();
        assertThat(content, containsString("Password:"));
        assertThat(content, not(containsString(config.getDisplayName())));
    }

    @Test public void detectRedundant() throws IOException {
        j.jenkins.setLabelString("rhel65");
        assertThat(config.detectRedundantLabels(j.jenkins), this.<LabelAtom>sameMembers());

        j.jenkins.setLabelString("rhel65 linux");
        assertThat(config.detectRedundantLabels(j.jenkins), this.sameMembers(j.jenkins.getLabelAtom("linux")));

        j.jenkins.setLabelString("rhel65 linux");
        assertThat(config.detectRedundantLabels(j.jenkins), this.sameMembers(j.jenkins.getLabelAtom("linux")));
    }

    private <T> TypeSafeMatcher<Collection<T>> sameMembers(Collection<T> items) {
        return new SameMembers<T>(items);
    }

    private <T> TypeSafeMatcher<Collection<T>> sameMembers(T... items) {
        return new SameMembers<T>(Arrays.asList(items));
    }

    private static class SameMembers<T> extends TypeSafeMatcher<Collection<T>> {

        private final Set<T> items;

        public SameMembers(Collection<T> items) {
            this.items = new HashSet<T>(items);
        }

        public void describeTo(Description description) {
            description.appendText("Implies: " + items);
        }

        @Override
        protected boolean matchesSafely(Collection<T> item) {
            return items.equals(new HashSet<T>(item));
        }
    }
}

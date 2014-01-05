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
import static org.junit.Assert.assertThat;
import hudson.model.Label;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
                new Implication("rhel || fedora", "linux")
        );
        config.implications(implications);
    }

    @Test public void roundtrip() {
        assertThat(config.implications(), equalTo(implications));

        assertThat(new Config().implications(), equalTo(implications));
    }

    @Test public void evaluate() throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.setLabelString("rhel65");
        config.evaluate(jenkins);

        HashSet<Label> expected = new HashSet<Label>(Arrays.asList(
                label("rhel65"), label("rhel6"), label("rhel"), label("linux"), label("master")
        ));

        assertThat(jenkins.getLabels(), equalTo((Set<Label>) expected));
    }

    private Label label(String label) {
        return Jenkins.getInstance().getLabelAtom(label);
    }

    @Test public void validateExpression() {
        assertThat(config.doCheckExpression(""), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression("master"), equalTo(FormValidation.ok()));
        assertThat(config.doCheckExpression("!master"), equalTo(FormValidation.ok()));

        assertThat(config.doCheckExpression("!||&&").getMessage(), containsString("Invalid boolean expression"));
    }
}

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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ImplicationTest {

    @Test
    void valid(JenkinsRule j) throws IOException {
        Jenkins.get().setLabelString("a");
        Implication i = new Implication("a||b", "c d");

        assertThat(i.atomsString(), equalTo("c d"));
        assertThat(i.expressionString(), equalTo("a||b"));
        assertThat(i.toString(), equalTo("a||b => c d"));
        assertThat(i.labelSize(), equalTo(1));
    }

    @Test
    void invalid(JenkinsRule j) {
        Implication i = new Implication("||", "c d");

        Set<LabelAtom> empty = Collections.emptySet();

        assertThat(i.atomsString(), equalTo("c d"));
        assertThat(i.expressionString(), equalTo(""));
        assertThat(i.toString(), equalTo("false => c d"));
        assertThat(i.infer(empty), equalTo(empty));
        assertThat(i.expression(), equalTo(null));
        assertThat(i.labelSize(), equalTo(0));
    }
}

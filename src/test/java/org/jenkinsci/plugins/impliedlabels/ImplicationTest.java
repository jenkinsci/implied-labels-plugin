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
import static org.junit.Assert.assertThat;
import hudson.model.labels.LabelAtom;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ImplicationTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void valid() {
        Implication i = new Implication("a||b", "c d");

        assertThat("c d", equalTo(i.atomsString()));
        assertThat("a||b", equalTo(i.expressionString()));
        assertThat("a||b => c d", equalTo(i.toString()));
    }

    @Test
    public void invalid() {
        Implication i = new Implication("||", "c d");

        Set<LabelAtom> empty = Collections.<LabelAtom>emptySet();

        assertThat("c d", equalTo(i.atomsString()));
        assertThat("", equalTo(i.expressionString()));
        assertThat("false => c d", equalTo(i.toString()));
        assertThat(i.infer(empty), equalTo((Collection<LabelAtom>) empty));
    }
}

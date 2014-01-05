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

import hudson.Util;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;

import antlr.ANTLRException;

public class Implication {

    private static final @Nonnull Set<LabelAtom> NO_ATOMS = Collections.emptySet();
    private final @Nonnull Set<LabelAtom> atoms;

    private final @Nullable Label expression;

    @DataBoundConstructor
    public Implication(String expression, String atoms) {

        this.atoms = Collections.unmodifiableSet(Label.parse(atoms));
        Label e;
        try {
            e = Label.parseExpression(expression);
        } catch (ANTLRException ex) {
            e = null;
        }
        this.expression = e;
    }

    public String expression() {
        return expression == null ? "" : expression.toString();
    }

    public String atoms() {
        return Util.join(atoms, " ");
    }

    public @Nonnull Collection<LabelAtom> infer(@Nonnull Collection<LabelAtom> atoms) {
        return expression != null && expression.matches(atoms)
                ? this.atoms
                : NO_ATOMS
        ;
    }

    @Override
    public String toString() {
        return (expression == null ? "false" : expression) + " => " + atoms();
    }

    @Override
    public int hashCode() {
        return 31 * atoms.hashCode() + expression().hashCode();
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs == null) return false;
        if (rhs == this) return true;
        if (rhs.getClass() != this.getClass()) return false;

        Implication other = (Implication) rhs;

        return atoms.equals(other.atoms) && expression().equals(other.expression());
    }
}

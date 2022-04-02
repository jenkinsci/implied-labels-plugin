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
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import antlr.ANTLRException;

@Restricted(NoExternalUse.class)
public class Implication {

    private static final @Nonnull Set<LabelAtom> NO_ATOMS = Collections.emptySet();
    private final @Nonnull Set<LabelAtom> atoms;

    private final @CheckForNull Label expression;

    @DataBoundConstructor
    public Implication(@Nonnull String expression, @Nonnull String atoms) {

        this.atoms = Collections.unmodifiableSet(Label.parse(atoms));
        Label e;
        try {
            e = Label.parseExpression(expression);
        } catch (ANTLRException ex) {
            e = null;
        }
        this.expression = e;
    }

    public String expressionString() {
        return expression == null ? "" : expression.toString();
    }

    public String atomsString() {
        return Util.join(atoms, " ");
    }

    public Label expression() {
        return expression;
    }

    public Set<LabelAtom> atoms() {
        return atoms;
    }

    public int labelSize() {
        if (expression == null) return 0;
        return expression.getNodes().size() + expression.getClouds().size();
    }

    public @Nonnull Collection<LabelAtom> infer(@Nonnull Collection<LabelAtom> atoms) {
        return expression != null && expression.matches(atoms)
                ? this.atoms
                : NO_ATOMS
        ;
    }

    @Override
    public String toString() {
        return (expression == null ? "false" : expression) + " => " + atomsString();
    }

    @Override
    public int hashCode() {
        return 31 * atoms.hashCode() + expressionString().hashCode();
    }

    @Override
    public boolean equals(Object rhs) {
        if (rhs == null) return false;
        if (rhs == this) return true;
        if (rhs.getClass() != this.getClass()) return false;

        Implication other = (Implication) rhs;

        if (!Objects.equals(atoms, other.atoms)) return false;
        return Objects.equals(expression, other.expression);
    }

    /*package*/ static @Nonnull List<Implication> sort(final @Nonnull Collection<Implication> implications) throws CycleDetectedException {
        CyclicGraphDetector<Implication> sorter = new ImplicationSorter(implications);

        sorter.run(implications);
        return sorter.getSorted();
    }

    private static final class ImplicationSorter extends CyclicGraphDetector<Implication> {
        private final Collection<Implication> implications;

        private ImplicationSorter(Collection<Implication> implications) {
            this.implications = implications;
        }

        @Override
        protected Iterable<Implication> getEdges(Implication current) {
            List<Implication> edges = new ArrayList<>();
            if (current.expression == null) return edges;

            for (Implication i: implications) {
                if (i == current) continue;
                if (i.expression == null) continue;

                if (Collections.disjoint(current.expression.listAtoms(), i.atoms)) continue;

                edges.add(i);
            }

            return edges;
        }
    }
}

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
import hudson.XmlFile;
import hudson.model.LabelFinder;
import hudson.model.ManagementLink;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import antlr.ANTLRException;

@Restricted(NoExternalUse.class)
public class Config extends ManagementLink {

    /**
     * Topologically sorted implications.
     */
    private @Nonnull List<Implication> implications = Collections.emptyList();

    public Config() {
        try {
            load();
        } catch (IOException ex) {
            // TODO Auto-generated catch block
            ex.printStackTrace();
        }
    }

    public String getDisplayName() {
        return "Label implications";
    }

    @Override
    public String getDescription() {
        return "Infer redundant labels automatically based on user declaration";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/implied-labels/icons/48x48/attribute.png";
    }

    @Override
    public String getUrlName() {
        return "label-implications";
    }

    @RequirePOST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        this.implications(req.bindJSONToList(
                Implication.class, req.getSubmittedForm().get("impl")
        ));
        rsp.sendRedirect2(Jenkins.getInstance().getRootUrl() + getUrlName());
    }

    /*package*/ void implications(@Nonnull Collection<Implication> implications) throws IOException {
        List<Implication> im;
        try {
            im = Implication.sort(implications);
        } catch (CycleDetectedException ex) {
            throw new IOException("Implication cycle detected", ex);
        }

        synchronized (this.implications) {
            this.implications = im;
            save();
        }
    }

    public @Nonnull List<Implication> implications() {
        return Collections.unmodifiableList(this.implications);
    }

    public @Nonnull Collection<LabelAtom> evaluate(@Nonnull Node node) {
        final @Nonnull Set<LabelAtom> labels = initialLabels(node);

        for(Implication i: implications) {
            labels.addAll(i.infer(labels));
        }
        return labels;
    }

    /*
     * Get labels to begin with. Those are configured labels, self label and labels contributed by other LabelFinders.
     * see hudson.model.Node#getDynamicLabels()
     */
    private @Nonnull Set<LabelAtom> initialLabels(@Nonnull Node node) {
        HashSet<LabelAtom> result = new HashSet<LabelAtom>();
        result.addAll(Label.parse(node.getLabelString()));
        result.add(node.getSelfLabel());

        for (LabelFinder labeler : LabelFinder.all()) {
            if (labeler instanceof Implier) continue; // skip Implier
            // Filter out any bad(null) results from plugins
            // for compatibility reasons, findLabels may return LabelExpression and not atom.
            for (Label label : labeler.findLabels(node))
                if (label instanceof LabelAtom) result.add((LabelAtom)label);
        }
        return result;
    }

    /**
     * Get list of configured labels that are explicitly declared but can be inferred using current implications
     */
    public @Nonnull Collection<LabelAtom> detectRedundantLabels(@Nonnull Node node) {
        final @Nonnull Set<LabelAtom> initial = initialLabels(node);
        final @Nonnull Set<LabelAtom> infered = new HashSet<LabelAtom>(implications.size());
        final @Nonnull Set<LabelAtom> accumulated = new HashSet<LabelAtom>(initial);

        for(Implication i: implications) {
            Collection<LabelAtom> ii = i.infer(accumulated);
            infered.addAll(ii);
            accumulated.addAll(ii);
        }

        infered.retainAll(initial);
        return infered;
    }

    private XmlFile getConfigFile() {
        final File file = new File(
                Jenkins.getInstance().root,
                getClass().getCanonicalName() + ".xml"
        );
        return new XmlFile(Jenkins.XSTREAM, file);
    }

    private void save() throws IOException {
        getConfigFile().write(this);
    }

    private void load() throws IOException {
        final XmlFile file = getConfigFile();
        if(file.exists()) {
            file.unmarshal(this);
        }
    }

    // see AbstractProject#doCheckAssignedLabelString
    @Restricted(NoExternalUse.class)
    public FormValidation doCheckExpression(@QueryParameter String expression) {
        if (Util.fixEmpty(expression) == null) return FormValidation.ok();

        try {

            Label.parseExpression(expression);
        } catch (ANTLRException ex) {

            return FormValidation.error(ex, hudson.model.Messages.AbstractProject_AssignedLabelString_InvalidBooleanExpression(ex.getMessage()));
        }
// since 1.544
//        return FormValidation.okWithMarkup(Messages.AbstractProject_LabelLink(
//                j.getRootUrl(), l.getUrl(), l.getNodes().size() + l.getClouds().size()
//        ));
        return FormValidation.ok();
    }

    @Restricted(NoExternalUse.class)
    public FormValidation doInferLabels(@QueryParameter String labelString) {
        if (Util.fixEmpty(labelString) == null) return FormValidation.ok();

        final @Nonnull Set<LabelAtom> labels = Label.parse(labelString);
        for(Implication i: implications) {
            labels.addAll(i.infer(labels));
        }

        labels.removeAll(Label.parse(labelString));

        if (labels.isEmpty()) return FormValidation.ok("No labels infered");

        return FormValidation.ok("Infered labels: %s", Util.join(labels, " "));
    }
}

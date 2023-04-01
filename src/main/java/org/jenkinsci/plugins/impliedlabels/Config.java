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

import antlr.ANTLRException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.CopyOnWrite;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Label;
import hudson.model.LabelFinder;
import hudson.model.ManagementLink;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

@Restricted(NoExternalUse.class)
public class Config extends ManagementLink {

    private static final @NonNull Logger CACHE_LOGGER = Logger.getLogger("ConfigCaching");

    /** Topologically sorted implications. */
    @GuardedBy("configLock")
    @CopyOnWrite
    private @NonNull List<Implication> implications = Collections.emptyList();

    @GuardedBy("configLock")
    private final transient @NonNull Map<Collection<LabelAtom>, Collection<LabelAtom>> cache = new HashMap<>();

    private final transient Object configLock = new Object();

    public Config() {
        try {
            load();
        } catch (IOException ex) {
            // TODO Auto-generated catch block
            ex.printStackTrace();
        }
    }

    @Override
    public ManagementLink.Category getCategory() {
        return ManagementLink.Category.CONFIGURATION;
    }

    public String getDisplayName() {
        return Messages.displayName();
    }

    @Override
    public String getDescription() {
        return Messages.infer_redundant_labels();
    }

    @Override
    public String getIconFileName() {
        return "/plugin/implied-labels/icons/48x48/attribute.png";
    }

    @Override
    public String getUrlName() {
        return "label-implications";
    }

    @POST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        this.implications(
                req.bindJSONToList(Implication.class, req.getSubmittedForm().get("impl")));
        rsp.sendRedirect("");
    }

    /*package*/ void implications(@NonNull Collection<Implication> implications) throws IOException {
        List<Implication> im;
        try {
            im = Collections.unmodifiableList(Implication.sort(implications));
        } catch (CycleDetectedException ex) {
            throw new IOException("Implication cycle detected", ex);
        }

        synchronized (configLock) {
            this.implications = im;
            CACHE_LOGGER.fine("Clearing cache when implications changed");
            this.cache.clear();
        }
        save();
    }

    public @NonNull List<Implication> implications() {
        synchronized (configLock) {
            return this.implications;
        }
    }

    public @NonNull Collection<LabelAtom> evaluate(@NonNull Node node) {
        final @NonNull Set<LabelAtom> initial = initialLabels(node);

        Collection<LabelAtom> labels;
        synchronized (configLock) {
            labels = cache.get(initial);
        }

        if (labels == null) {
            labels = new HashSet<>(initial);
            for (Implication i : implications()) {
                labels.addAll(i.infer(labels));
            }

            synchronized (configLock) {
                CACHE_LOGGER.fine("Caching " + initial + " -> " + labels);
                cache.put(initial, labels);
            }
        }

        return labels;
    }

    /*
     * Get labels to begin with. Those are configured labels, self label and labels contributed by other LabelFinders.
     * see hudson.model.Node#getDynamicLabels()
     */
    private @NonNull Set<LabelAtom> initialLabels(@NonNull Node node) {
        final HashSet<LabelAtom> result = new HashSet<>(Label.parse(node.getLabelString()));
        result.add(node.getSelfLabel());

        for (LabelFinder labeler : LabelFinder.all()) {
            if (labeler instanceof Implier) continue; // skip Implier
            // Filter out any bad(null) results from plugins
            // for compatibility reasons, findLabels may return LabelExpression and not atom.
            for (Label label : labeler.findLabels(node)) if (label instanceof LabelAtom) result.add((LabelAtom) label);
        }
        return result;
    }

    /**
     * Get list of configured labels that are explicitly declared but can be inferred using current
     * implications
     */
    public @NonNull Collection<LabelAtom> detectRedundantLabels(@NonNull Node node) {
        final @NonNull Set<LabelAtom> initial = initialLabels(node);
        final @NonNull Set<LabelAtom> infered = new HashSet<>();
        final @NonNull Set<LabelAtom> accumulated = new HashSet<>(initial);

        for (Implication i : implications()) {
            Collection<LabelAtom> ii = i.infer(accumulated);
            infered.addAll(ii);
            accumulated.addAll(ii);
        }

        infered.retainAll(initial);
        return infered;
    }

    private XmlFile getConfigFile() {
        final File file = new File(Jenkins.get().root, getClass().getCanonicalName() + ".xml");
        return new XmlFile(Jenkins.XSTREAM, file);
    }

    private void save() throws IOException {
        getConfigFile().write(this);
    }

    private void load() throws IOException {
        final XmlFile file = getConfigFile();
        if (file.exists()) {
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

            return FormValidation.error(ex, Messages.invalid_label_expression());
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

        final @NonNull Set<LabelAtom> labels = Label.parse(labelString);
        for (Implication i : implications()) {
            labels.addAll(i.infer(labels));
        }

        labels.removeAll(Label.parse(labelString));

        if (labels.isEmpty()) return FormValidation.ok(Messages.no_labels_inferred());

        return FormValidation.ok(Messages.inferred_labels(Util.join(labels, " ")));
    }

    @Restricted(NoExternalUse.class)
    public AutoCompletionCandidates doAutoCompleteLabels(@QueryParameter String value) {
        AutoCompletionCandidates candidates = new AutoCompletionCandidates();

        for (LabelAtom atom : Jenkins.get().getLabelAtoms()) {
            if (atom.getName().startsWith(value)) {
                candidates.add(atom.getName());
            }
        }
        for (Implication i : implications()) {
            for (LabelAtom atom : i.atoms()) {
                if (atom.getName().startsWith(value)) {
                    candidates.add(atom.getName());
                }
            }
        }

        return candidates;
    }
}

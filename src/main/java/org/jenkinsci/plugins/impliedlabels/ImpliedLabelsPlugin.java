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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.XmlFile;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.impliedlabels.Implication.ImplicationWrapper;
import org.kohsuke.stapler.StaplerRequest;

@Extension
@Symbol("impliedLabels")
public class ImpliedLabelsPlugin extends GlobalConfiguration {

    public static ImpliedLabelsPlugin get() {
        return Jenkins.get().getExtensionList(ImpliedLabelsPlugin.class).get(0);
    }

    @SuppressFBWarnings("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
    public ImpliedLabelsPlugin() {
        load();
    }

    public Config getConfig() {
        return Jenkins.get().getExtensionList(Config.class).get(0);
    }

    @Override
    protected XmlFile getConfigFile() {
        return getConfig().getConfigFile();
    }

    public void setImplications(List<ImplicationWrapper> implications) {
        try {
            this.getConfig()
                    .implications(implications.stream()
                            .map(p -> new Implication(p.getExpression(), p.getAtoms()))
                            .collect(Collectors.toList()));
        } catch (IOException e) {

        }
    }

    public List<ImplicationWrapper> getImplications() {
        return this.getConfig().implications().stream()
                .map(i -> new ImplicationWrapper(i.expressionString(), i.atomsString()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject jsonObject) throws FormException {
        setImplications(Collections.emptyList());
        req.bindJSON(this, jsonObject);
        save();
        return false;
    }
}

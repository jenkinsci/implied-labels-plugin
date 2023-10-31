package org.jenkinsci.plugins.impliedlabels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;

public class ConfigurationAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void should_support_configuration_as_code() throws Exception {
        ImpliedLabelsPlugin plugin =
                Jenkins.get().getExtensionList(ImpliedLabelsPlugin.class).get(0);
        assertEquals(3, plugin.getConfig().implications().size());
        assertEquals("ubuntu1", plugin.getConfig().implications().get(0).atomsString());
        assertEquals("ubuntu2", plugin.getConfig().implications().get(1).atomsString());
        assertEquals("ubuntu3", plugin.getConfig().implications().get(2).atomsString());
        assertEquals("test1||test2", plugin.getConfig().implications().get(0).expressionString());
        assertEquals("test3||test4", plugin.getConfig().implications().get(1).expressionString());
        assertEquals("test5||test6", plugin.getConfig().implications().get(2).expressionString());
    }
}

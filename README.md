# Implied Labels Plugin

Infer dynamic node labels using configured implications.

Lets have a node with `linux`, `RHEL`, `RHEL8` and `RHEL8_5` labels.
Most of this information is redundant and can be inferred from the `RHEL8_5`
label. Here is an excerpt of the Implied Labels Plugin configuration to do
just that.

![](docs/images/implied-labels.png)

This plugin gathers the implication rules to one place (*Manage Jenkins
\> Label implications*), so explicit node labels can be defined in more
concise form (without redundancy). Implications are readable for every
user with `Jenkins.READ`, but only `Jenkins.ADMINISTER` is authorized to
configure. The plugin also detects explicitly configured node labels that
can be inferred using existing implication rules.

## Details

Jenkins administrators can declare any number of implication rules for
label inference. An implication consists of *label expression* and *atom
list*. A node that matches *label expression* will have assigned new
labels from *atom list*. Labels contributed from the Implied Labels Plugin
are dynamic labels and thus not saved in configuration. Implications are
evaluated in topological order so implication expressions can refer to
labels contributed by other implications.

## Configuration as code

Label definitions can be automated with [configuration as code](https://plugins.jenkins.io/configuration-as-code/).
The [platform labeler](https://plugins.jenkins.io/platformlabeler/) can automatically assign labels based on operating system properties and those operating system properties can be used to define more labels.

```
unclassified:
  impliedLabels:
    implications:
    - atoms: "linux"
      expression: "Ubuntu || CentOS || Debian || Rocky || openSUSE"
```

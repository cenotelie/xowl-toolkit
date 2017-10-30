# xOWL Toolkit #

xOWL Toolkit is a Maven plugin for the packaging of xOWL-related products.
This plugin can be used to packaged xOWL platforms (and derived) based on [Apache Felix](http://felix.apache.org/), xOWL Platform Add-ons and xOWL Marketplaces.


## How do I use this software? ##

### Package a xOWL Platform from base Apache Felix ###

1. Specify the `xowl-platform` as packaging type for the Maven module.

2. Add [Apache Felix](http://felix.apache.org/) modules and your modules as Maven dependencies.
The dependencies will be deployed as bundles in the Felix distribution.
*Note that the first dependency must be the base Felix distribution.*
For example:

```
<dependencies>
    <dependency>
        <groupId>org.apache.felix</groupId>
        <artifactId>org.apache.felix.main.distribution</artifactId>
        <version>5.6.1</version>
        <type>tar.gz</type>
        <scope>compile</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.felix</groupId>
        <artifactId>org.apache.felix.configadmin</artifactId>
        <version>1.8.12</version>
        <scope>compile</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.felix</groupId>
        <artifactId>org.apache.felix.eventadmin</artifactId>
        <version>1.4.8</version>
        <scope>compile</scope>
    </dependency>
    ... your dependencies here
</dependencies>
```

3. Add the xOWL Toolkit Maven plugin:

```
<plugin>
    <groupId>org.xowl.toolkit</groupId>
    <artifactId>xowl-packaging-maven-plugin</artifactId>
    <version>2.0.1</version>
    <extensions>true</extensions>
    <configuration>
        <icon>Project-relative path to a file for the icon</icon>
        <licenseFullText>Path to the file contains the full text for the license</licenseFullText>
        <versionScmTag>SCM tag</versionScmTag>
        <versionBuildTag>Build name</versionBuildTag>
        <versionBuildTimestamp>Timestamp</versionBuildTimestamp>
        <resources>
            <!-- List of files and directories that will be included as resources at the root of the platform distribution -->
            <param>src/main/resources/config</param>
            <param>../LICENSE.txt</param>
        </resources>
    </configuration>
</plugin>
```

### Package a derived xOWL Platform ###

A derived xOWL Platform is a xOWL platform that is based on and extends another xOWL Platform.
The setup to package such as platform is similar to the setup for a base one as explained above.
The sole difference is that the first Maven dependency must be the xOWL platform to derive from in place of the base Felix distribution.
For example, to create a platform based on the standard xOWL Platform:

```
<dependencies>
    <dependency>
        <groupId>org.xowl.platform</groupId>
        <artifactId>xowl-distribution</artifactId>
        <type>xowl-platform</type>
        <version>${xowlPlatformVersion}</version>
        <scope>compile</scope>
    </dependency>
    ... your dependencies here
</dependencies>
```

### Package a xOWL Platform Addon ###

A xOWL Platform Addon is a packaged set of OSGi bundles that can be deployed into a xOWL Platform.
They form a coherent whole in order to provide features and customization for the platform.
A xOWL Platform Addon is simply defined as a Maven module:

1. Specify the `xowl-addon` as packaging type for the Maven module.

2. Add as Maven dependencies the bundles that constitute the Addon:

```
<dependencies>
    ... your dependencies here
</dependencies>
```

3. Add the xOWL Toolkit Maven plugin:

```
<plugin>
    <groupId>org.xowl.toolkit</groupId>
    <artifactId>xowl-packaging-maven-plugin</artifactId>
    <version>2.0.1</version>
    <extensions>true</extensions>
    <configuration>
        <icon>Project-relative path to a file for the icon</icon>
        <licenseFullText>Path to the file contains the full text for the license</licenseFullText>
        <versionScmTag>SCM tag</versionScmTag>
        <versionBuildTag>Build name</versionBuildTag>
        <versionBuildTimestamp>Timestamp</versionBuildTimestamp>
        <pricing>Description of the pricing policy for this Addon.</pricing>
        <tags>
            <!-- List of tags for this Addon -->
            <param>systems</param>
            <param>engineering</param>
        </tags>
    </configuration>
</plugin>
```

### Package a xOWL Marketplace ###

A xOWL Marketplace defines a set of available Addons that can be deployed on a xOWL Platform.
A xOWL Marketplace has a specific file layout that is produced by this Maven plugin.
A xOWL Marketplace is simply defined as a Maven module:

1. Specify the `xowl-marketplace` as packaging type for the Maven module.

2. Add as Maven dependencies the xOWL Platform Addons that constitute this Marketplace:

```
<dependencies>
    <dependency>
        <groupId>com.seriousbusiness</groupId>
        <artifactId>my-addon</artifactId>
        <version>1.0.0</version>
        <type>xowl-addon</type>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

3. Add the xOWL Toolkit Maven plugin:

```
<plugin>
    <groupId>org.xowl.toolkit</groupId>
    <artifactId>xowl-packaging-maven-plugin</artifactId>
    <version>2.0.1</version>
    <extensions>true</extensions>
</plugin>
```

## How to build ##

To build the artifacts in this repository using Maven:

```
$ mvn clean install -Dgpg.skip=true
```


## How can I contribute? ##

The simplest way to contribute is to:

* Fork this repository on [Bitbucket](https://bitbucket.org/cenotelie/xowl-toolkit).
* Fix [some issue](https://bitbucket.org/cenotelie/xowl-toolkit/issues?status=new&status=open) or implement a new feature.
* Create a pull request on Bitbucket.

Patches can also be submitted by email, or through the [issue management system](https://bitbucket.org/cenotelie/xowl-toolkit/issues).

The [isse tracker](https://bitbucket.org/cenotelie/xowl-toolkit/issues) may contain tickets that are accessible to newcomers. Look for tickets with `[beginner]` in the title. These tickets are good ways to become more familiar with the project and the codebase.


## License ##

This software is licenced under the Lesser General Public License (LGPL) v3.
Refers to the `LICENSE.txt` file at the root of the repository for the full text, or to [the online version](http://www.gnu.org/licenses/lgpl-3.0.html).
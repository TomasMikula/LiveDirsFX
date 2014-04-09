LiveDirsFX
==========

LiveDirsFX is a combination of directory watcher, directory-tree model (for [TreeView](http://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/TreeView.html)) and simple asynchronous file I/O facility. The extra benefits of this combination are:
 1. Automatic synchronization of the directory model with the filesystem.
 2. Ability to distinguish directory structure and file modifications made by the application (through the I/O facility) from external modifications.


Example
-------

```java
enum ChangeSource {
    INTERNAL, // indicates a change made by this application
    EXTERNAL, // indicates an external change
}

// create LiveDirs to watch a directory
LiveDirs<ChangeSource> liveDirs = new LiveDirs<>(EXTERNAL);
Path dir = Paths.get("/path/to/watched/directory/");
liveDirs.addTopLevelDirectory(dir);

// use LiveDirs as a TreeView model
DirectoryModel<ChangeSource> model = liveDirs.model();
TreeView<Path> treeView = new TreeView<>(model.getRoot());
treeView.setShowRoot(false);

// handle external changes
model.modifications().subscribe(m -> {
    if(m.getInitiator() == EXTERNAL) {
        reload(m.getPath());
    } else {
        // modification done by this application, no extra action needed
    }
});

// make filesystem changes via LiveDirs's I/O facility to be able to
// distinguish them from external changes
Path file = dir.resolve("some/file.txt");
liveDirs.io().saveUTF8File(file, "Hello text file!", INTERNAL);

// clean up
liveDirs.dispose();
```


Use LiveDirsFX in your project
------------------------------

### Method 1: as a managed dependency (recommended)

Snapshot releases are deployed to Sonatype snapshot repository with these Maven coordinates

| Group ID            | Artifact ID | Version        |
| :-----------------: | :---------: | :------------: |
| org.fxmisc.livedirs | livedirsfx  | 1.0.0-SNAPSHOT |

#### Gradle example

```groovy
repositories {
    maven {
        url 'https://oss.sonatype.org/content/repositories/snapshots/' 
    }
}

dependencies {
    compile group: 'org.fxmisc.livedirs', name: 'livedirsfx', version: '1.0.0-SNAPSHOT'
}
```

#### Sbt example

```scala
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "org.fxmisc.livedirs" % "livedirsfx" % "1.0.0-SNAPSHOT"
```


### Method 2: as an unmanaged dependency

Download the latest [JAR](https://oss.sonatype.org/content/repositories/snapshots/org/fxmisc/livedirs/livedirsfx/1.0.0-SNAPSHOT/) or [fat JAR (including dependencies)](https://googledrive.com/host/0B4a5AnNnZhkbMzRneXVNUEI3anc/downloads/) and place it on your classpath.


Links
-----

[Javadoc](http://www.fxmisc.org/livedirs/javadoc/org/fxmisc/livedirs/package-summary.html)

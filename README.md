# Global Version Catalog

The provided sample showcases the implementation of a global version catalog in a multi-module Gradle project to standardize dependency versions across all builds.
This strategy ensures that builds and their subprojects are compiled with the versions specified in the global catalog, rather than their individually specified versions.
Within our example, we have `libB`, which is dependent on `libA` version 1, and an application that depends on both `libA` version 2 and `libB` version 1.
All components are configured to be publishable to a simulated repository using the command `./gradlew publish`.

## Without Global Catalog

The initial scenario compiles and executes the application, utilizing binary dependencies for `libA` and `libB`. 
During runtime, a version conflict arises due to `libB` being compiled against `libA` version 1 and the `app` compiled against `libA` version 2. The conflict is only present on the runtime scope and resolves to `libA` version 2 within the `app`.
Each component operates with its designated version catalog.

```
$ ./gradlew run
> Task :compileDebugCpp
> Task :linkDebug
> Task :installDebug

> Task :run
app compile against libA v2
app runs against libA v2
libB compiled against libA v1
libB runs against libA v2

BUILD SUCCESSFUL
```

## With Global Catalog

The second scenario builds and runs the `app` and `libB` component together, but this time employing a global version catalog provided by the root build.
In this case, `libB` reflects that it was compiled against `libA` version 2, demonstrating the overriding effect of the global version catalog.

```
$ ./gradlew run -Pbuild-libB-from-source=true
Duplicate entry for alias 'libA': dependency {group='com.example', name='libA', version='1'} is replaced with dependency {group='com.example', name='libA', version='2'}
> Task :compileDebugCpp
> Task :libB:compileDebugCpp
> Task :libB:linkDebug
> Task :linkDebug
> Task :installDebug

> Task :run
app compile against libA v2
app runs against libA v2
libB compiled against libA v2
libB runs against libA v2

BUILD SUCCESSFUL
```

## Limitations

An important limitation to note in this approach is that Gradle permits only a single version file to be loaded per catalog.
To amalgamate multiple files, a manual process of loading and merging is required.
This constraint necessitates additional steps to achieve unified version management across complex builds.

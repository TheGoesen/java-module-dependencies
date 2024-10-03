/*
 * Copyright the GradleX team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradlex.javamodule.dependencies.initialization;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Module {

    /**
     * The directory, relative to the build root directory, in which the Module is located.
     */
    public abstract Property<String> getDirectory();

    /**
     * The 'artifact' name of the Module. This corresponds to the Gradle subproject name. If the Module is published
     * to a Maven repository, this is the 'artifact' in the 'group:artifact' identifier to address the published Jar.
     */
    public abstract Property<String> getArtifact();

    /**
     * The 'group' of the Module. This corresponds to setting the 'group' property in a build.gradle file. If the
     * Module is published to a Maven repository, this is the 'group' in the 'group:artifact' identifier to address
     * the published Jar. The group needs to be configured here (rather than in build.gradle files) for the plugin
     * to support additional Modules inside a subproject that other modules depend on, such as a 'testFixtures' module.
     */
    public abstract Property<String> getGroup();

    /**
     * The paths of the module-info.java files inside the project directory. Usually, this does not need to be adjusted.
     * By default, it contains all 'src/$sourceSetName/java/module-info.java' files that exist.
     */
    public abstract ListProperty<String> getModuleInfoPaths();

    /**
     * {@link Module#plugin(String)}
     */
    public abstract ListProperty<String> getPlugins();

    @Inject
    public Module(File root) {
        getArtifact().convention(getDirectory().map(f -> Paths.get(f).getFileName().toString()));
        getModuleInfoPaths().convention(getDirectory().map(projectDir -> listChildren(root, projectDir + "/src")
                .map(srcDir -> new File(srcDir, "java/module-info.java"))
                .filter(File::exists)
                .map(moduleInfo -> "src/" + moduleInfo.getParentFile().getParentFile().getName() + "/java")
                .collect(Collectors.toList())));
    }

    /**
     * Apply a plugin to the Module project. This is the same as using the 'plugins { }' block in the Module's
     * build.gradle file. Applying plugins here allows you to omit build.gradle files completely.
     */
    public void plugin(String id) {
        getPlugins().add(id);
    }

    private Stream<File> listChildren(File root, String projectDir) {
        System.out.println("root = " + root);
        System.out.println("projectDir = " + projectDir);
        File[] children = new File(root, projectDir).listFiles();
        return children == null ? Stream.empty() : Arrays.stream(children);
    }
}

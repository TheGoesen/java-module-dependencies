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

import org.gradle.api.*;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlatformExtension;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradlex.javamodule.dependencies.JavaModuleDependenciesExtension;
import org.gradlex.javamodule.dependencies.JavaModuleDependenciesPlugin;
import org.gradlex.javamodule.dependencies.JavaModuleVersionsPlugin;
import org.gradlex.javamodule.dependencies.internal.utils.ModuleInfoCache;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;

public abstract class JavaModulesExtension {

    private final Settings settings;
    private final ModuleInfoCache moduleInfoCache;
    private final Map<String, Module> ourProjectPaths;
    private final Collection<String> versionPaths;

    @Inject
    public abstract ObjectFactory getObjects();

    @Inject
    public abstract ProviderFactory getProviders();

    @Inject
    public JavaModulesExtension(Settings settings) {
        this.settings = settings;
        this.moduleInfoCache = getObjects().newInstance(ModuleInfoCache.class, true);
        ourProjectPaths = new HashMap<>();
        versionPaths = new HashSet<>();
        settings.getGradle().settingsEvaluated(new Action<Settings>() {
            @Override
            public void execute(Settings settings) {
                if (!versionPaths.isEmpty()) {
                    settings.getGradle().getLifecycle().beforeProject(new ApplyJavaModuleVersionsPluginAction(versionPaths));
                }
                if (ourProjectPaths.isEmpty()) {
                    return;
                }
                settings.getGradle().getLifecycle().beforeProject(new ApplyPluginsAction(ourProjectPaths, moduleInfoCache));

            }
        });

    }

    /**
     * {@link JavaModulesExtension#module(String, Action)}
     */
    public void module(String directory) {
        module(directory, m -> {});
    }

    /**
     * Register and configure Module located in the given folder, relative to the build root directory.
     */
    public void module(String directory, Action<Module> action) {
        Module module = getObjects().newInstance(Module.class, settings.getRootDir());
        module.getDirectory().set(directory);
        action.execute(module);
        includeModule(module, new File(settings.getRootDir(), module.getDirectory().get()));
    }

    /**
     * {@link JavaModulesExtension#directory(String, Action)}
     */
    public void directory(String directory) {
        directory(directory, m -> {});
    }

    /**
     * Register and configure ALL Modules located in direct subfolders of the given folder.
     */
    public void directory(String directory, Action<Directory> action) {
        File modulesDirectory = new File(settings.getRootDir(), directory);
        Directory moduleDirectory = getObjects().newInstance(Directory.class, modulesDirectory);
        action.execute(moduleDirectory);


        for (Module module : moduleDirectory.customizedModules.values()) {
            includeModule(module, new File(modulesDirectory, module.getDirectory().get()));
        }
        Provider<List<String>> listProvider = getProviders().of(ValueSourceDirectoryListing.class, new Action<ValueSourceSpec<ValueSourceDirectoryListing.DirectoryListingParameter>>() {
            @Override
            public void execute(ValueSourceSpec<ValueSourceDirectoryListing.DirectoryListingParameter> spec) {
                spec.getParameters().getRegexExclusions().set(moduleDirectory.getExclusions());
                spec.getParameters().getExclusions().set(moduleDirectory.customizedModules.keySet());
                spec.getParameters().getDir().set(modulesDirectory);
                spec.getParameters().getRequiresBuildFile().set(moduleDirectory.getRequiresBuildFile());
            }
        });

        for (String projectDir : listProvider.get()) {
            Module module = moduleDirectory.addModule(projectDir);
            if (!module.getModuleInfoPaths().get().isEmpty()) {
                // only auto-include if there is at least one module-info.java
                includeModule(module, new File(modulesDirectory, projectDir));
            }
        }
    }

    /**
     * Configure a subproject as Platform for defining Module versions.
     */
    public void versions(String directory) {
        String projectName = Paths.get(directory).getFileName().toString();
        settings.include(projectName);
        ProjectDescriptor project = settings.project(":" + projectName);
        project.setProjectDir(new File(settings.getRootDir(), directory));
        versionPaths.add(project.getPath());

    }

    private void includeModule(Module module, File projectDir) {
        String artifact = module.getArtifact().get();
        settings.include(artifact);
        ProjectDescriptor project = settings.project(":" + artifact);
        project.setProjectDir(projectDir);
        ourProjectPaths.put(project.getPath(), module);
        for (String path : module.getModuleInfoPaths().get()) {
            moduleInfoCache.put(projectDir, path,
                    module.getArtifact().get(), module.getGroup(), settings.getProviders());
        }


    }

    @NonNullApi
    private static class ApplyPluginsAction implements IsolatedAction<Project>, Action<Project> {


        private final Map<String, Module> ourProjectPaths;
        private final ModuleInfoCache moduleInfoCache;

        public ApplyPluginsAction(Map<String, Module> ourProjectPaths, ModuleInfoCache moduleInfoCache) {
            this.ourProjectPaths = ourProjectPaths;
            this.moduleInfoCache = moduleInfoCache;
        }

        @Override
        public void execute(Project project) {
            String projectPath = project.getPath();
            Module module = ourProjectPaths.get(projectPath);
            if (module == null) {
                return; // not included by us...
            }
            Property<String> group = module.getGroup();
            if (group.isPresent()) {
                project.setGroup(group.get());
            }
            project.getPlugins().apply(JavaModuleDependenciesPlugin.class);
            project.getExtensions().getByType(JavaModuleDependenciesExtension.class).getModuleInfoCache().set(moduleInfoCache);
            module.getPlugins().get().forEach(id -> project.getPlugins().apply(id));

            project.getPlugins().withType(ApplicationPlugin.class, new Action<ApplicationPlugin>() {
                @Override
                public void execute(ApplicationPlugin applicationPlugin) {
                    Provider<String> mainModuleName = getMainModuleName(project);
                    project.getExtensions().getByType(JavaApplication.class).getMainModule().set(mainModuleName);
                }

                private Provider<String> getMainModuleName(Project project) {
                    SourceSetContainer byType = project.getExtensions().getByType(SourceSetContainer.class);
                    return byType.named("main").map(sourceSet -> moduleInfoCache.get(sourceSet, project.getProviders()).getModuleName());

                }
            });


        }
    }

    @NonNullApi
    private static class ApplyJavaModuleVersionsPluginAction implements IsolatedAction<Project>, Action<Project> {

        private final Collection<String> projects;

        public ApplyJavaModuleVersionsPluginAction(Collection<String> projects) {
            this.projects = projects;
        }

        @Override
        public void execute(Project project) {
            if (projects.contains(project.getPath())) {
                project.getPlugins().apply(JavaPlatformPlugin.class);
                project.getPlugins().apply(JavaModuleVersionsPlugin.class);
                project.getExtensions().getByType(JavaPlatformExtension.class).allowDependencies();
            }
        }
    }
}

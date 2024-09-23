package org.gradlex.javamodule.dependencies.internal.utils;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.*;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class ValueSourceModuleInfo implements ValueSource<ModuleInfo, ValueSourceModuleInfo.ModuleInfoSourceP> {




    interface ModuleInfoSourceP extends ValueSourceParameters{

        Property<FileCollection> getLocations();
    }


    @Override
    public @Nullable ModuleInfo obtain() {
        ModuleInfoSourceP parameters = getParameters();
        for (File fileSystemLocation : parameters.getLocations().get()) {
            File file = new File(fileSystemLocation, "module-info.java");
            if (file.isFile()) {
                try {
                    return new ModuleInfo(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


        }
        return null;
    }
}

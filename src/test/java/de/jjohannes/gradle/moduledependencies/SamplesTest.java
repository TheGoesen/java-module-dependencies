package de.jjohannes.gradle.moduledependencies;

import org.gradle.exemplar.test.runner.SampleModifiers;
import org.gradle.exemplar.test.runner.SamplesRoot;
import org.gradle.exemplar.test.runner.SamplesRunner;
import org.junit.runner.RunWith;

@RunWith(SamplesRunner.class)
@SamplesRoot("samples")
@SampleModifiers(PluginBuildLocationSampleModifier.class)
public class SamplesTest {

}

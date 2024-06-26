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

package org.gradlex.javamodule.dependencies.internal.diagnostics;

import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.internal.logging.text.StyledTextOutput;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

public class StyledNodeRenderer implements NodeRenderer {

    @Override
    public void renderNode(StyledTextOutput output, RenderableDependency node, boolean alreadyRendered) {
        String name = node.getName();
        if (name.startsWith("[BOM]")) {
            output.withStyle(Description).text(name);
        } else if (name.startsWith("[AUTO]")) {
            output.withStyle(Failure).text(name);
        } else if (name.startsWith("[CLASSPATH]")) {
            output.withStyle(Failure).text(name);
        } else {
            int idx = name.indexOf('|');
            output.text(name.substring(0, idx)).withStyle(Description).text(name.substring(idx));
        }
        switch (node.getResolutionState()) {
            case FAILED:
                output.withStyle(Failure).text(" FAILED");
                break;
            case RESOLVED:
                if (alreadyRendered && !node.getChildren().isEmpty()) {
                    output.withStyle(Info).text(" (*)");
                }
                break;
            case RESOLVED_CONSTRAINT:
                output.withStyle(Info).text(" (c)");
                break;
            case UNRESOLVED:
                output.withStyle(Info).text(" (n)");
                break;
        }
    }
}

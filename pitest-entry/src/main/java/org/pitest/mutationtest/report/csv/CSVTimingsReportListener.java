/*
 * Copyright 2011 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.report.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.util.ResultOutputStrategy;
import org.pitest.util.TimeSpan;
import org.pitest.util.Timings;
import org.pitest.util.Unchecked;

public class CSVTimingsReportListener implements MutationResultListener {

    private final Writer out;
    private final Timings timings;

    public CSVTimingsReportListener(final ResultOutputStrategy outputStrategy, final Timings timings) {
        this(outputStrategy.createWriterForFile("mutation-timings.csv"), timings);
    }

    public CSVTimingsReportListener(final Writer out, final Timings timings) {
        this.out = out;
        this.timings = timings;
    }

    private String makeCsv(final Object... os) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i != os.length; i++) {
            sb.append(os[i].toString());
            if (i != (os.length - 1)) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    @Override
    public void runStart() {
        try {
            this.out.write(makeCsv("stage", "timing")
                    + System.getProperty("line.separator"));
        } catch (IOException ex) {
            throw Unchecked.translateCheckedException(ex);
        }
    }

    @Override
    public void runEnd() {
        final Map<Timings.Stage, TimeSpan> timingsMap = this.timings.getTimings();
        final List<Map.Entry<Timings.Stage, TimeSpan>> sortedEntries = timingsMap
                .entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry1.getKey().compareTo(entry2.getKey()))
                .collect(Collectors.toList());
        for (Map.Entry<Timings.Stage, TimeSpan> entry : sortedEntries) {
            try {
                this.out.write(makeCsv(entry.getKey().toString().replaceAll(" ", ""), entry.getValue().duration())
                        + System.getProperty("line.separator"));
            } catch (IOException ex) {
                throw Unchecked.translateCheckedException(ex);
            }
        }
        try {
            this.out.close();
        } catch (final IOException e) {
            throw Unchecked.translateCheckedException(e);
        }
    }

    @Override
    public void handleMutationResult(final ClassMutationResults metaData) {
        // Nothing to see here
    }

}

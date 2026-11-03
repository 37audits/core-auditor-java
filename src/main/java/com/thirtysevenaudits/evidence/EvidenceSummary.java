/*
 * Copyright © 2026 37 Audits (thiago.moreira@37audits.com)
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
package com.thirtysevenaudits.evidence;

/**
 * What became of a run's evidence, reported in the {@link com.thirtysevenaudits.auditor.Response}.
 *
 * @param uploaded
 *            whether the evidence file was stored at the request's upload URL.
 * @param entries
 *            number of entries in the file.
 * @param bytes
 *            size of the gzipped file.
 * @param dropped
 *            entries left out because the file reached its limits (see {@link EvidenceRecorder}).
 */
public record EvidenceSummary(boolean uploaded, int entries, long bytes, int dropped) {
}

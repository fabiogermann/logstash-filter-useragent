/**
 * Copyright 2012 Twitter, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Portions Copyright 2012-2017 Elasticsearch, Inc
 */

package org.logstash.uaparser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Device parser using ua-parser regexes. Extracts device information from user agent strings.
 * @author Steve Jiang (@sjiang) <gh at iamsteve com>
 */
final class DeviceParser {

    private final List<DeviceParser.DevicePattern> patterns;

    public static DeviceParser fromList(List<Map<String, String>> configList) {
        List<DeviceParser.DevicePattern> configPatterns = new ArrayList<>();
        for (Map<String, String> configMap : configList) {
            configPatterns.add(DeviceParser.patternFromMap(configMap));
        }
        return new DeviceParser(configPatterns);
    }

    /**
     * Ctor.
     * @param patterns Regex Patterns
     */
    private DeviceParser(List<DeviceParser.DevicePattern> patterns) {
        this.patterns = patterns;
    }

    public Device parse(String agentString) {
        if (agentString == null) {
            return null;
        }
        Device device = null;
        for (final DeviceParser.DevicePattern p : this.patterns) {
            if ((device = p.match(agentString)) != null) {
                break;
            }
        }
        if (device == null) device = new Device("Other", null, null);
        return device;
    }

    private static DeviceParser.DevicePattern patternFromMap(Map<String, String> configMap) {
        final String regex = configMap.get("regex");
        if (regex == null) {
            throw new IllegalArgumentException("Device is missing regex");
        }
        Pattern pattern = "i".equals(configMap.get("regex_flag")) // no other flags used (by now)
            ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE) : Pattern.compile(regex);
        return new DeviceParser.DevicePattern(pattern, configMap.get("device_replacement"), configMap.get("brand_replacement"), configMap.get("model_replacement"));
    }

    private static final class DevicePattern {

        private static final Pattern SUBSTITUTIONS_PATTERN = Pattern.compile("\\$\\d");

        private final Matcher matcher;

        private final String deviceReplacement;
        private final String brandReplacement;
        private final String modelReplacement;

        DevicePattern(Pattern pattern, String deviceReplacement, String brandReplacement, String modelReplacement) {
            this.matcher = pattern.matcher("");
            this.deviceReplacement = deviceReplacement;
            this.brandReplacement = brandReplacement;
            this.modelReplacement = modelReplacement;
        }

        public synchronized Device match(final CharSequence agentString) {
            this.matcher.reset(agentString);
            if (!this.matcher.find()) {
                return null;
            }
            String family = null;
            String brand = null;
            String model = null;
            if (this.deviceReplacement != null) {
                if (this.deviceReplacement.contains("$")) {
                    family = this.deviceReplacement;
                    for (String substitution : DevicePattern
                        .getSubstitutions(this.deviceReplacement)) {
                        int i = Integer.parseInt(substitution.substring(1));
                        final String replacement = this.matcher.groupCount() >= i &&
                            this.matcher.group(i) != null
                            ? Matcher.quoteReplacement(this.matcher.group(i)) : "";
                        family = family.replaceFirst('\\' + substitution, replacement);
                    }
                    family = family.trim();
                } else {
                    family = this.deviceReplacement;
                }
            } else if (this.matcher.groupCount() >= 1) {
                family = this.matcher.group(1);
            }
            if (this.brandReplacement != null) {
                if (this.brandReplacement.contains("$")) {
                    brand = this.brandReplacement;
                    for (String substitution : DevicePattern
                            .getSubstitutions(this.brandReplacement)) {
                        int i = Integer.parseInt(substitution.substring(1));
                        final String replacement = this.matcher.groupCount() >= i &&
                                this.matcher.group(i) != null
                                ? Matcher.quoteReplacement(this.matcher.group(i)) : "";
                        brand = brand.replaceFirst('\\' + substitution, replacement);
                    }
                    brand = brand.trim();
                } else {
                    brand = this.brandReplacement;
                }
            }
            if (this.modelReplacement != null) {
                if (this.modelReplacement.contains("$")) {
                    model = this.modelReplacement;
                    for (String substitution : DevicePattern
                            .getSubstitutions(this.modelReplacement)) {
                        int i = Integer.parseInt(substitution.substring(1));
                        final String replacement = this.matcher.groupCount() >= i &&
                                this.matcher.group(i) != null
                                ? Matcher.quoteReplacement(this.matcher.group(i)) : "";
                        model = model.replaceFirst('\\' + substitution, replacement);
                    }
                    model = model.trim();
                } else {
                    model = this.modelReplacement;
                }
            }
            return new Device(family, brand, model);
        }

        private static Iterable<String> getSubstitutions(String deviceReplacement) {
            Matcher matcher =
                DeviceParser.DevicePattern.SUBSTITUTIONS_PATTERN.matcher(deviceReplacement);
            List<String> substitutions = new ArrayList<>();
            while (matcher.find()) {
                substitutions.add(matcher.group());
            }
            return substitutions;
        }

    }

}

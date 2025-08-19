package assettracking.label.service.template;

import assettracking.label.model.template.LabelElement;
import assettracking.label.model.template.LabelTemplate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZplGeneratorService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    public String generate(LabelTemplate template, Map<String, String> data) {
        StringBuilder zpl = new StringBuilder();
        zpl.append("^XA");
        zpl.append(String.format("^PW%d^LL%d", template.getWidth(), template.getHeight()));

        for (LabelElement element : template.getElements()) {
            String elementZpl = element.toZpl();
            Matcher matcher = VARIABLE_PATTERN.matcher(elementZpl);
            StringBuilder finalElementZpl = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = data.getOrDefault(key, "");
                matcher.appendReplacement(finalElementZpl, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(finalElementZpl);
            zpl.append(finalElementZpl.toString());
        }

        zpl.append("^XZ");
        return zpl.toString();
    }

    public Set<String> findVariables(LabelTemplate template) {
        Set<String> variables = new HashSet<>();
        for (LabelElement element : template.getElements()) {
            Matcher matcher = VARIABLE_PATTERN.matcher(element.toZpl());
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
        }
        return variables;
    }
}
/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.cli;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public final class ListComponentsCli {
  private ListComponentsCli() {
  }

  public static void main(String[] args) {
    final var options = new Options();
    options.addOption(Option.builder("o")
        .longOpt("out")
        .hasArg()
        .desc("Write component metadata JSON to the given file path.")
        .build());
    options.addOption(new Option("h", "help", false, "Show help."));

    for (final var arg : args) {
      if ("-h".equals(arg) || "--help".equals(arg)) {
        printHelp(options);
        return;
      }
    }

    final CommandLine cmd;
    try {
      cmd = new DefaultParser().parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      printHelp(options);
      System.exit(2);
      return;
    }

    try {
      final var loader = new Loader(null);
      final var file = LogisimFile.createEmpty(loader);
      for (final var lib : loader.getBuiltin().getLibraries()) {
        file.addLibrary(lib);
      }

      final var components = collectComponents(file.getLibraries());
      final var json = renderJson(components);

      final var out = cmd.getOptionValue("out");
      if (out != null) {
        Files.writeString(Path.of(out), json, StandardCharsets.UTF_8);
      } else {
        System.out.println(json);
      }
      System.exit(0);
    } catch (Exception e) {
      System.err.println("Failed to list components: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private static void printHelp(Options options) {
    final var formatter = new HelpFormatter();
    formatter.printHelp("ListComponentsCli", options, true);
  }

  private static List<ComponentInfo> collectComponents(List<Library> libraries) {
    final var components = new ArrayList<ComponentInfo>();
    for (final var lib : libraries) {
      for (final var tool : lib.getTools()) {
        if (tool instanceof AddTool addTool) {
          final var attrs = addTool.getAttributeSet();
          components.add(ComponentInfo.from(lib, addTool, attrs));
        }
      }
    }
    components.sort(Comparator.comparing(ComponentInfo::name, String.CASE_INSENSITIVE_ORDER));
    return components;
  }

  private static String renderJson(List<ComponentInfo> components) {
    final var builder = new StringBuilder();
    builder.append("{\"components\":[");
    for (var i = 0; i < components.size(); i++) {
      if (i > 0) builder.append(",");
      builder.append(components.get(i).toJson());
    }
    builder.append("]}");
    return builder.toString();
  }

  private static String escape(String value) {
    final var builder = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      final var ch = value.charAt(i);
      switch (ch) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (ch <= 0x1F) {
            builder.append(String.format("\\u%04x", (int) ch));
          } else {
            builder.append(ch);
          }
        }
      }
    }
    return builder.toString();
  }

  private record AttributeInfo(String name, String description, String defaultValue) {
    String toJson() {
      final var builder = new StringBuilder();
      builder.append("{\"name\":\"").append(escape(name)).append("\",");
      builder.append("\"description\":\"").append(escape(description)).append("\",");
      builder.append("\"default\":\"").append(escape(defaultValue)).append("\"}");
      return builder.toString();
    }
  }

  private record ComponentInfo(String name, String library, List<AttributeInfo> attributes) {
    static ComponentInfo from(Library library, AddTool addTool, AttributeSet attrs) {
      final var attrInfos = new ArrayList<AttributeInfo>();
      for (final var attr : attrs.getAttributes()) {
        if (attr.isHidden()) {
          continue;
        }
        attrInfos.add(attributeInfo(attrs, attr));
      }
      attrInfos.sort(Comparator.comparing(AttributeInfo::name, String.CASE_INSENSITIVE_ORDER));
      return new ComponentInfo(addTool.getName(), library.getDisplayName(), attrInfos);
    }

    private static AttributeInfo attributeInfo(AttributeSet attrs, Attribute<?> attr) {
      final var value = attrs.getValue(attr);
      final var display = attr.toDisplayString(value);
      return new AttributeInfo(attr.getName(), attr.getDisplayName(), display);
    }

    String toJson() {
      final var builder = new StringBuilder();
      builder.append("{\"name\":\"").append(escape(name)).append("\",");
      builder.append("\"library\":\"").append(escape(library)).append("\",");
      builder.append("\"attributes\":[");
      for (var i = 0; i < attributes.size(); i++) {
        if (i > 0) builder.append(",");
        builder.append(attributes.get(i).toJson());
      }
      builder.append("]}");
      return builder.toString();
    }
  }
}

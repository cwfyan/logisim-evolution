/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.cli;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.ComponentXmlEmitter;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.w3c.dom.Element;

public final class EmitComponentCli {
  private EmitComponentCli() {
  }

  public static void main(String[] args) {
    final var options = new Options();
    options.addOption(Option.builder("c")
        .longOpt("component")
        .hasArg()
        .required()
        .desc("Component factory name, e.g. \"Pin\", \"AND Gate\", \"Splitter\".")
        .build());
    options.addOption(Option.builder("a")
        .longOpt("attr")
        .hasArg()
        .desc("Attribute override (repeatable), e.g. --attr facing=west.")
        .build());
    options.addOption(Option.builder("l")
        .longOpt("loc")
        .hasArg()
        .desc("Location for the component, e.g. 40,30 or (40,30). Defaults to 0,0.")
        .build());
    options.addOption(Option.builder()
        .longOpt("xml-out")
        .hasArg()
        .desc("Write the component XML fragment to the given file path.")
        .build());
    options.addOption(Option.builder()
        .longOpt("xml-pretty")
        .desc("Pretty-print the component XML (applies to JSON and xml-out).")
        .build());
    options.addOption(Option.builder()
        .longOpt("pins-out")
        .hasArg()
        .desc("Write the pin layout JSON array to the given file path.")
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

    final var componentName = cmd.getOptionValue("component");
    final var loc = parseLocation(cmd.getOptionValue("loc", "0,0"));

    try {
      final var loader = new Loader(null);
      final var file = LogisimFile.createEmpty(loader);
      final var match = findTool(loader.getBuiltin().getLibraries(), componentName);
      if (match == null) {
        System.err.println("Component not found: " + componentName);
        System.err.println("Available components:");
        for (final var name : collectComponentNames(loader.getBuiltin().getLibraries())) {
          System.err.println("  - " + name);
        }
        System.exit(1);
        return;
      }

      file.addLibrary(match.library());
      final var factory = match.tool().getFactory();
      final var attrs = (AttributeSet) match.tool().getAttributeSet().clone();
      final var overrides = cmd.getOptionValues("attr");
      applyDefaultAppearance(attrs, overrides);
      applyAttributes(attrs, overrides);
      final var component = factory.createComponent(loc, attrs);

      final var xmlElement = ComponentXmlEmitter.toElement(file, loader, component);
      final var prettyXml = cmd.hasOption("xml-pretty");
      final var xml = elementToString(xmlElement, prettyXml);
      final var xmlOut = cmd.getOptionValue("xml-out");
      if (xmlOut != null) {
        Files.writeString(Path.of(xmlOut), xml, StandardCharsets.UTF_8);
      }
      final var pins = collectPins(component);
      final var pinsOut = cmd.getOptionValue("pins-out");
      if (pinsOut != null) {
        Files.writeString(Path.of(pinsOut), renderPinsJson(pins), StandardCharsets.UTF_8);
      }
      final var bbox = computeBounds(component);

      final var output = renderJson(componentName, loc, xml, bbox, pins);
      System.out.println(output);
      System.exit(0);
    } catch (Exception e) {
      System.err.println("Failed to emit component: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private static String elementToString(Element element, boolean pretty) throws TransformerException {
    if (element == null) return "";
    final var tfFactory = TransformerFactory.newInstance();
    final Transformer transformer = tfFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    transformer.setOutputProperty(OutputKeys.INDENT, pretty ? "yes" : "no");
    if (pretty) {
      try {
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      } catch (IllegalArgumentException ignored) {
        // Do nothing
      }
    }
    final var writer = new StringWriter();
    transformer.transform(new DOMSource(element), new StreamResult(writer));
    return writer.toString().trim();
  }

  private static void printHelp(Options options) {
    final var formatter = new HelpFormatter();
    formatter.printHelp("EmitComponentCli", options, true);
  }

  private static Location parseLocation(String value) {
    return Location.parse(value);
  }

  private static ToolMatch findTool(List<Library> libraries, String componentName) {
    for (final var lib : libraries) {
      for (final var tool : lib.getTools()) {
        if (tool instanceof AddTool addTool) {
          final var factory = addTool.getFactory();
          if (factory == null) continue;
          if (factory.getName().equalsIgnoreCase(componentName)
              || addTool.getName().equalsIgnoreCase(componentName)) {
            return new ToolMatch(lib, addTool);
          }
        }
      }
    }
    return null;
  }

  private static List<String> collectComponentNames(List<Library> libraries) {
    final var names = new ArrayList<String>();
    for (final var lib : libraries) {
      for (final var tool : lib.getTools()) {
        if (tool instanceof AddTool addTool) {
          names.add(addTool.getName());
        }
      }
    }
    names.sort(String::compareToIgnoreCase);
    return names;
  }

  private static void applyAttributes(AttributeSet attrs, String[] overrides) {
    if (overrides == null) return;
    for (final var override : overrides) {
      final var eq = override.indexOf('=');
      if (eq <= 0 || eq == override.length() - 1) {
        throw new IllegalArgumentException("Invalid attribute override: " + override);
      }
      final var name = override.substring(0, eq).trim();
      final var value = override.substring(eq + 1).trim();
      final var attr = findAttribute(attrs, name);
      if (attr == null) {
        throw new IllegalArgumentException("Unknown attribute: " + name);
      }
      applyAttribute(attrs, attr, value);
    }
  }

  private static void applyDefaultAppearance(AttributeSet attrs, String[] overrides) {
    final var appearanceAttr = findAttribute(attrs, "appearance");
    if (appearanceAttr == null) return;
    if (overrides != null) {
      for (final var override : overrides) {
        final var eq = override.indexOf('=');
        if (eq <= 0) continue;
        final var name = override.substring(0, eq).trim();
        if (appearanceAttr.getName().equalsIgnoreCase(name)) {
          return;
        }
      }
    }
    applyAttribute(attrs, appearanceAttr, "classic");
  }

  private static Attribute<?> findAttribute(AttributeSet attrs, String name) {
    for (final var attr : attrs.getAttributes()) {
      if (attr.getName().equalsIgnoreCase(name)) {
        return attr;
      }
    }
    return null;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void applyAttribute(AttributeSet attrs, Attribute attr, String value) {
    attrs.setValue(attr, attr.parse(value));
  }

  private static List<PinInfo> collectPins(Component component) {
    final var pins = new ArrayList<PinInfo>();
    final var ends = component.getEnds();
    final var instance = Instance.getInstanceFor(component);
    final List<Port> ports = instance == null ? null : instance.getPorts();
    for (var i = 0; i < ends.size(); i++) {
      final var end = ends.get(i);
      final var portName = resolvePortName(ports, i);
      pins.add(PinInfo.from(component, end, portName, i));
    }
    return pins;
  }

  private static String resolvePortName(List<Port> ports, int index) {
    if (ports != null && index < ports.size()) {
      final var tip = ports.get(index).getToolTip();
      if (tip != null && !tip.isBlank()) {
        return tip;
      }
    }
    return "port" + index;
  }

  private static String renderJson(
      String componentName,
      Location loc,
      String xml,
      BoundsInfo bbox,
      List<PinInfo> pins) {
    final var escapedComponent = escape(componentName);
    final var escapedXml = escape(xml);
    final var builder = new StringBuilder();
    builder.append("{");
    builder.append("\"component\":\"").append(escapedComponent).append("\",");
    builder.append("\"location\":").append("{\"x\":").append(loc.getX())
        .append(",\"y\":").append(loc.getY()).append("},");
    builder.append("\"componentXml\":\"").append(escapedXml).append("\",");
    builder.append("\"bbox\":").append(bbox.toJson()).append(",");
    builder.append("\"pins\":[");
    for (var i = 0; i < pins.size(); i++) {
      if (i > 0) builder.append(",");
      builder.append(pins.get(i).toJson());
    }
    builder.append("]");
    builder.append("}");
    return builder.toString();
  }

  private static BoundsInfo computeBounds(Component component) {
    final var bounds = component.getBounds();
    final var loc = component.getLocation();
    final var left = bounds.getX();
    final var top = bounds.getY();
    final var right = bounds.getX() + bounds.getWidth();
    final var bottom = bounds.getY() + bounds.getHeight();
    return new BoundsInfo(
        left,
        top,
        right,
        bottom,
        left - loc.getX(),
        top - loc.getY(),
        right - loc.getX(),
        bottom - loc.getY());
  }

  private static String renderPinsJson(List<PinInfo> pins) {
    final var builder = new StringBuilder();
    builder.append("[");
    for (var i = 0; i < pins.size(); i++) {
      if (i > 0) builder.append(",");
      builder.append(pins.get(i).toJson());
    }
    builder.append("]");
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

  private record PinInfo(
      String name,
      String direction,
      int width,
      int absX,
      int absY,
      int offsetX,
      int offsetY,
      int index) {
    static PinInfo from(Component component, EndData end, String name, int index) {
      final var loc = component.getLocation();
      final var endLoc = end.getLocation();
      final var direction = end.isInput() && end.isOutput()
          ? "inout"
          : end.isInput() ? "input" : "output";
      return new PinInfo(
          name,
          direction,
          end.getWidth().getWidth(),
          endLoc.getX(),
          endLoc.getY(),
          endLoc.getX() - loc.getX(),
          endLoc.getY() - loc.getY(),
          index);
    }

    String toJson() {
      final var builder = new StringBuilder();
      builder.append("{\"name\":\"").append(escape(name)).append("\",");
      builder.append("\"direction\":\"").append(direction).append("\",");
      builder.append("\"width\":").append(width).append(",");
      builder.append("\"index\":").append(index).append(",");
      builder.append("\"absolute\":{\"x\":").append(absX).append(",\"y\":").append(absY).append("},");
      builder.append("\"offset\":{\"dx\":").append(offsetX).append(",\"dy\":").append(offsetY).append("}");
      builder.append("}");
      return builder.toString();
    }
  }

  private record BoundsInfo(
      int left,
      int top,
      int right,
      int bottom,
      int offsetLeft,
      int offsetTop,
      int offsetRight,
      int offsetBottom) {
    String toJson() {
      final var builder = new StringBuilder();
      builder.append("{\"absolute\":{");
      builder.append("\"left\":").append(left).append(",");
      builder.append("\"top\":").append(top).append(",");
      builder.append("\"right\":").append(right).append(",");
      builder.append("\"bottom\":").append(bottom).append("},");
      builder.append("\"offset\":{");
      builder.append("\"topLeft\":\"").append(formatPoint(offsetLeft, offsetTop)).append("\",");
      builder.append("\"bottomRight\":\"").append(formatPoint(offsetRight, offsetBottom)).append("\"}}");
      return builder.toString();
    }
  }

  private static String formatPoint(int x, int y) {
    return "(" + x + "," + y + ")";
  }

  private record ToolMatch(Library library, AddTool tool) {
  }

}

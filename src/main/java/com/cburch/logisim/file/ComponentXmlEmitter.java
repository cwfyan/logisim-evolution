/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import com.cburch.logisim.comp.Component;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;

public final class ComponentXmlEmitter {
  private ComponentXmlEmitter() {
  }

  public static Element toElement(LogisimFile file, LibraryLoader loader, Component comp)
      throws ParserConfigurationException, LoadFailedException, IOException {
    return XmlWriter.componentToElement(file, loader, comp);
  }
}

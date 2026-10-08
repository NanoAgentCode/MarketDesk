package com.marketdesk.ui;

import static org.junit.Assert.*;

import java.nio.file.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.*;

public class MainNavigationLayoutTest {
  private static final String ANDROID = "http://schemas.android.com/apk/res/android";

  private Element layout() throws Exception {
    Path path = Path.of("src/main/res/layout/activity_main.xml");
    if (!Files.exists(path)) path = Path.of("app/src/main/res/layout/activity_main.xml");
    assertTrue("main layout must exist", Files.exists(path));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
  }

  private Element find(Element root, String id) {
    NodeList nodes = root.getElementsByTagName("*");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element node = (Element) nodes.item(i);
      if (id.equals(node.getAttributeNS(ANDROID, "id"))) return node;
    }
    throw new AssertionError("Missing " + id);
  }

  @Test
  public void singlePageHostIsFixedAndOutsideTheScrollView() throws Exception {
    Element root = layout(), host = find(root, "@+id/single_page_content");
    assertSame(root, host.getParentNode());
    assertEquals("LinearLayout", host.getTagName());
    assertEquals("0dp", host.getAttributeNS(ANDROID, "layout_height"));
    assertEquals("1", host.getAttributeNS(ANDROID, "layout_weight"));
    assertEquals("gone", host.getAttributeNS(ANDROID, "visibility"));
  }

  @Test
  public void navigationIsOutsideScrollingContentAndLastInTheRoot() throws Exception {
    Element root = layout(), dock = find(root, "@+id/navigation_tabs");
    assertEquals("LinearLayout", root.getTagName());
    assertEquals("vertical", root.getAttributeNS(ANDROID, "orientation"));
    assertSame(root, dock.getParentNode());
    Element last = null;
    for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element) last = (Element) node;
    }
    assertSame(dock, last);
    assertFalse(dock.getParentNode().equals(find(root, "@+id/page_scroll")));
  }

  @Test
  public void scrollingContentReservesSpaceForTheDock() throws Exception {
    Element root = layout(), scroll = find(root, "@+id/page_scroll");
    assertEquals("0dp", scroll.getAttributeNS(ANDROID, "layout_height"));
    assertEquals("1", scroll.getAttributeNS(ANDROID, "layout_weight"));
    assertSame(root, scroll.getParentNode());
    assertSame(scroll, find(root, "@+id/page_content").getParentNode());
  }

  @Test
  public void floatingDockHasSpacingAndElevation() throws Exception {
    Element dock = find(layout(), "@+id/navigation_tabs");
    assertEquals("12dp", dock.getAttributeNS(ANDROID, "layout_marginTop"));
    assertEquals("8dp", dock.getAttributeNS(ANDROID, "elevation"));
    assertEquals("wrap_content", dock.getAttributeNS(ANDROID, "layout_height"));
  }
}

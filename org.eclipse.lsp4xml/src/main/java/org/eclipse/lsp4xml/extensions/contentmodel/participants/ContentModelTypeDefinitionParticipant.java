package org.eclipse.lsp4xml.extensions.contentmodel.participants;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;

import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4xml.dom.DOMAttr;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.dom.DOMElement;
import org.eclipse.lsp4xml.dom.DOMNode;
import org.eclipse.lsp4xml.dom.DOMParser;
import org.eclipse.lsp4xml.extensions.contentmodel.model.CMDocument;
import org.eclipse.lsp4xml.extensions.contentmodel.model.CMElementDeclaration;
import org.eclipse.lsp4xml.extensions.contentmodel.model.ContentModelManager;
import org.eclipse.lsp4xml.extensions.xsd.utils.XSDUtils;
import org.eclipse.lsp4xml.services.extensions.ITypeDefinitionParticipant;
import org.eclipse.lsp4xml.services.extensions.ITypeDefinitionRequest;
import org.eclipse.lsp4xml.uriresolver.URIResolverExtensionManager;
import org.eclipse.lsp4xml.utils.URIUtils;
import org.eclipse.lsp4xml.utils.XMLPositionUtility;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.io.Files;

public class ContentModelTypeDefinitionParticipant implements ITypeDefinitionParticipant {

	@Override
	public void findTypeDefinition(ITypeDefinitionRequest request, List<LocationLink> locations,
			CancelChecker cancelChecker) {
		ContentModelManager contentModelManager = request.getComponent(ContentModelManager.class);
		DOMNode node = request.getNode();
		if (node == null) {
			return;
		}
		if (node.isElement()) {
			DOMElement element = (DOMElement) node;
			CMDocument cmDocument = contentModelManager.findCMDocument(element.getOwnerDocument(),
					element.getNamespaceURI());
			if (cmDocument != null) {
				CMElementDeclaration elementDeclaration = cmDocument.findCMElement(element, element.getNamespaceURI());
				if (elementDeclaration != null) {
					String documentURI = cmDocument.getURI();
					if (URIUtils.isFileResource(documentURI)) {
						URIResolverExtensionManager resolverExtensionManager = request
								.getComponent(URIResolverExtensionManager.class);
						try {
							
							DOMDocument schema = DOMParser.getInstance().parse(
									Files.asCharSource(new File(new URI(documentURI).getPath()), Charset.defaultCharset()).read(),
									documentURI, resolverExtensionManager);
							NodeList children = schema.getDocumentElement().getChildNodes();
							for (int i = 0; i < children.getLength(); i++) {
								Node n = children.item(i);
								if (n.getNodeType() == Node.ELEMENT_NODE) {
									Element elt = (Element) n;
									if (XSDUtils.isXSElement(elt)) {
										if (element.getLocalName().equals(elt.getAttribute("name"))) {
											DOMAttr targetAttr = (DOMAttr) elt.getAttributeNode("name");
											LocationLink location = XMLPositionUtility.createLocationLink(element,
													targetAttr.getNodeAttrValue());
											locations.add(location);
										}
									}
								}
							}

						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					System.err.println(elementDeclaration);
				}
			}

		} else if (node.isAttribute()) {
			DOMAttr attr = (DOMAttr) node;
		}
	}

}

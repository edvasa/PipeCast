package org.schabi.newpipe.cast.protocols.upnp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.schabi.newpipe.cast.Device;
import org.schabi.newpipe.cast.MediaFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class UpnpDevice extends Device {
    private Document description;
    private Element device;
    private URL avTransportUrl;
    private URL connectionManagerUrl;

    public UpnpDevice(String location) throws IOException, ParserConfigurationException, SAXException {
        super(location);
        getDescription();
    }

    private void getDescription() throws IOException, ParserConfigurationException, SAXException {
        URL url = new URL(location);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = input.readLine()) != null) {
            response.append(line);
        }
        input.close();

        InputSource inputSource = new InputSource(new StringReader(response.toString()));
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        description = documentBuilder.parse(inputSource);
        description.getDocumentElement().normalize();
        device = (Element) description.getDocumentElement().getElementsByTagName("device").item(0);

        Element urlBase = (Element) description.getDocumentElement().getElementsByTagName("URLBase").item(0);
        URL baseUrl;
        if (urlBase == null) {
            baseUrl = new URL(location);
        } else {
            baseUrl = new URL(urlBase.getTextContent());
        }

        Element serviceList = (Element) device.getElementsByTagName("serviceList").item(0);
        NodeList services = serviceList.getElementsByTagName("service");
        int servicesLength = services.getLength();
        for (int i = 0; i < servicesLength; i++) {
            Element service = (Element) services.item(i);
            if (service.getElementsByTagName("serviceType").item(0).getTextContent().equals("urn:schemas-upnp-org:service:AVTransport:1")) {
                String serviceUrl = service.getElementsByTagName("controlURL").item(0).getTextContent();
                avTransportUrl = new URL(baseUrl, serviceUrl);
            } else if (service.getElementsByTagName("serviceType").item(0).getTextContent().equals("urn:schemas-upnp-org:service:ConnectionManager:1")) {
                String serviceUrl = service.getElementsByTagName("controlURL").item(0).getTextContent();
                connectionManagerUrl = new URL(baseUrl, serviceUrl);
            }
        }
    }

    @Override
    public String getName() {
        return device.getElementsByTagName("friendlyName").item(0).getTextContent();
    }

    private void play() throws IOException, XMLStreamException {
        HttpURLConnection connection = (HttpURLConnection) avTransportUrl.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml;charset=utf-8");
        connection.setRequestProperty("Soapaction", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"");
        OutputStream outputStream = connection.getOutputStream();

        StringWriter sw = new StringWriter();
        XMLOutputFactory xmlof = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = xmlof.createXMLStreamWriter(sw);

        writer.writeStartDocument("utf-8", "1.0");
        writer.writeStartElement("s:Envelope");
        writer.writeAttribute("s:encodingStyle", "http://schemas.xmlsoap.org/soap/encoding/");
        writer.writeNamespace("s", "http://schemas.xmlsoap.org/soap/envelope/");
        writer.writeStartElement("s:Body");
        writer.writeStartElement("u:Play");
        writer.writeNamespace("u", "urn:schemas-upnp-org:service:AVTransport:1");
        writer.writeStartElement("InstanceID");
        writer.writeCharacters("0");
        writer.writeEndElement();
        writer.writeStartElement("Speed");
        writer.writeCharacters("1");
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        byte[] xml = sw.toString().getBytes();
        outputStream.write(xml);
        outputStream.close();
        connection.getInputStream();
    }

    @Override
    public void play(String url, String title, String creator, MediaFormat mediaFormat) throws IOException, XMLStreamException {
        HttpURLConnection connection = (HttpURLConnection) avTransportUrl.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml;charset=utf-8");
        connection.setRequestProperty("Soapaction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"");
        connection.setDoOutput(true);
        OutputStream outputStream = connection.getOutputStream();

        StringWriter didlSw = new StringWriter();
        XMLOutputFactory didlXmlof = XMLOutputFactory.newInstance();
        XMLStreamWriter didlWriter = didlXmlof.createXMLStreamWriter(didlSw);
        didlWriter.writeStartElement("DIDL-Lite");
        didlWriter.writeNamespace("", "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/");
        didlWriter.writeNamespace("dc", "http://purl.org/dc/elements/1.1/");
        didlWriter.writeNamespace("dlna", "urn:schemas-dlna-org:metadata-1-0/");
        didlWriter.writeNamespace("pv", "http://www.pv.com/pvns/");
        didlWriter.writeNamespace("sec", "http://www.sec.co.kr/");
        didlWriter.writeNamespace("upnp", "urn:schemas-upnp-org:metadata-1-0/upnp/");
        didlWriter.writeStartElement("item");
        didlWriter.writeAttribute("id", "/test/123");
        didlWriter.writeAttribute("parentID", "/test");
        didlWriter.writeAttribute("restricted", "1");
        didlWriter.writeStartElement("upnp:class");
        didlWriter.writeCharacters(mediaFormat.upnpClass);
        didlWriter.writeEndElement();
        didlWriter.writeStartElement("dc:title");
        didlWriter.writeCharacters(title);
        didlWriter.writeEndElement();
        didlWriter.writeStartElement("dc:creator");
        didlWriter.writeCharacters(creator);
        didlWriter.writeEndElement();
        didlWriter.writeStartElement("res");

        String protocolInfo = "http-get:*:" + mediaFormat.mimeType + ":";
        if (mediaFormat.dlnaProfile == null) {
            protocolInfo += "*";
        } else {
            protocolInfo += "DLNA.ORG_PN=" + mediaFormat.dlnaProfile;
        }

        didlWriter.writeAttribute("protocolInfo", protocolInfo); // TODO: add more DLNA-specific stuff
        didlWriter.writeCharacters(url);
        didlWriter.writeEndElement();
        didlWriter.writeEndElement();
        didlWriter.writeEndElement();
        didlWriter.writeEndDocument();
        didlWriter.close();

        StringWriter sw = new StringWriter();
        XMLOutputFactory xmlof = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = xmlof.createXMLStreamWriter(sw);
        writer.writeStartDocument("utf-8", "1.0");
        writer.writeStartElement("s:Envelope");
        writer.writeAttribute("s:encodingStyle", "http://schemas.xmlsoap.org/soap/encoding/");
        writer.writeNamespace("s", "http://schemas.xmlsoap.org/soap/envelope/");
        writer.writeStartElement("s:Body");
        writer.writeStartElement("u:SetAVTransportURI");
        writer.writeNamespace("u", "urn:schemas-upnp-org:service:AVTransport:1");
        writer.writeStartElement("InstanceID");
        writer.writeCharacters("0");
        writer.writeEndElement();
        writer.writeStartElement("CurrentURI");
        writer.writeCharacters(url);
        writer.writeEndElement();
        writer.writeStartElement("CurrentURIMetaData");
        writer.writeCharacters(didlSw.toString());
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        byte[] xml = sw.toString().getBytes();
        outputStream.write(xml);
        outputStream.close();
        connection.getInputStream();

        play();
    }

    @Override
    public List<MediaFormat> getSupportedFormats() throws IOException, XMLStreamException, ParserConfigurationException, SAXException {
        HttpURLConnection connection = (HttpURLConnection) connectionManagerUrl.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml;charset=utf-8");
        connection.setRequestProperty("Soapaction", "\"urn:schemas-upnp-org:service:ConnectionManager:1#GetProtocolInfo\"");
        connection.setDoOutput(true);
        OutputStream outputStream = connection.getOutputStream();

        StringWriter sw = new StringWriter();
        XMLOutputFactory xmlof = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = xmlof.createXMLStreamWriter(sw);
        writer.writeStartDocument("utf-8", "1.0");
        writer.writeStartElement("s:Envelope");
        writer.writeAttribute("s:encodingStyle", "http://schemas.xmlsoap.org/soap/encoding/");
        writer.writeNamespace("s", "http://schemas.xmlsoap.org/soap/envelope/");
        writer.writeStartElement("s:Body");
        writer.writeStartElement("u:GetProtocolInfo");
        writer.writeNamespace("u", "urn:schemas-upnp-org:service:ConnectionManager:1");
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        byte[] xml = sw.toString().getBytes();
        outputStream.write(xml);
        outputStream.close();

        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = input.readLine()) != null) {
            response.append(line);
        }
        input.close();

        System.out.println(response.toString());

        InputSource inputSource = new InputSource(new StringReader(response.toString()));
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(inputSource);
        document.getDocumentElement().normalize();
        Element body = (Element) document.getDocumentElement().getElementsByTagName("s:Body").item(0);
        Element getProtocolInfoResponse = (Element) body.getElementsByTagName("u:GetProtocolInfoResponse").item(0);
        String sinkText = getProtocolInfoResponse.getElementsByTagName("Sink").item(0).getTextContent();

        List<MediaFormat> supportedFormats = new ArrayList<MediaFormat>();

        String[] sinks = sinkText.split(",");
        for (String sink : sinks) {
            String[] splittedSink = sink.split(":");
            if (splittedSink[0].equals("http-get"))  {
                for (MediaFormat mediaFormat : MediaFormat.values()) {
                    if (splittedSink[2].equals(mediaFormat.mimeType)) {
                        supportedFormats.add(mediaFormat);
                    }
                }
            }
        }

        return supportedFormats;
    }
}

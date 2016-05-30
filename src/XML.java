import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

import com.sio.model.AbstractAccessPoint;
import com.sio.model.Tag;
import com.sio.model.WirelessTag;
import com.sio.plugin.Terminal;


public class XML extends Terminal {
	public static final String FILE_NAME = "taglist.xml";
	public static final String TAG_LAABEL = "tag";
	public static final String MAC = "mac";
	public static final String IP = "ip";
	public static final String BATT = "batt";
	public static final String SERIALIZE = "serialize";
	public static final String INTERVAL = "interval";
	public static final String SIGNAL = "signal";
	public static final String TYPE = "type";
	public static final String HARDWARE = "hardware";
	public static final String SWITCH = "switch";
	public static final String STATE = "state";
	public static final String COMMUNICATION = "comm";
	public static final String TAG_COUNT = "tag_count";
	/**
	 * <b>IP,SERIALIZE,INTERVAL,SIGNAL,TYPE,SWITCH,STATE,COMMUNICATION,HARDWARE,BATT</b>
	 */
	public static final String[] ATTRIBUTES = new String [] {
		IP,SERIALIZE,INTERVAL,SIGNAL,TYPE,SWITCH,STATE,COMMUNICATION,HARDWARE,BATT
	};
	private static List<String> macs = new ArrayList<>();
	private static File xmlFile;
	private static Document doc = new Document(new Element("tags"));

//	timer
	private long last_output;
	private static final int REFRESH = 2*1000;
	
	
	@Override
	public void start() {
		File folder = getOutputFolderPathFile();
		xmlFile = new File(folder, FILE_NAME);
		if(folder.exists()){
			if(xmlFile.exists()){
//				initialize builder
				SAXBuilder builder = new SAXBuilder();
				try {
					doc = builder.build(xmlFile);
				} catch (JDOMException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
//				clear file
				clrTag();
			} else {
//				create file
				try {
					xmlFile.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
//			create directory
			folder.mkdirs();
		}
	}

	@Override
	public void onEvent() {
		if(System.currentTimeMillis() - last_output >= REFRESH){
			 Set<AbstractAccessPoint> aps = getDevices();
			 for(AbstractAccessPoint ap : aps){
				 Collection<WirelessTag> tags = new HashSet<>();
				 tags.addAll(ap.getTags());
				 for(WirelessTag wTag : tags){
					 Tag tag = wTag.getTag();
					 if(isTagExist(tag.mac())){
						 setState(tag.mac(), ATTRIBUTES, tag.apIP(),tag.code_1(),tag.code_2(),tag.signal(),tag.model(),tag.on(),"online",wTag.getCommuncation(),tag.error(),tag.battary());
					 } else {
						 addTag(tag.mac());
						 setState(tag.mac(), ATTRIBUTES, tag.apIP(),tag.code_1(),tag.code_2(),tag.signal(),tag.model(),tag.on(),"online",wTag.getCommuncation(),tag.error(),tag.battary());
					 }
				 }
			 }
			 exportXML();
			 last_output = System.currentTimeMillis();
		}
	}

	@Override
	public void stop() {
		clrTag();
	}
	
	private void clrTag() {
		doc.getRootElement().removeContent();
		doc.getRootElement().setAttribute(TAG_COUNT, "0");
		macs.clear();
	}
	
	private boolean isTagExist(String mac){
		return macs.contains(mac);
	}
	
	private void addTag(String mac){
		if (!macs.contains(mac)) {
			macs.add(mac);
			Element ele = new Element(TAG_LAABEL);
			Element macTag = new Element(MAC);
			Element ipTag = new Element(IP);
			Element battTag = new Element(BATT);
			Element serialTag = new Element(SERIALIZE);
			Element intervallTag = new Element(INTERVAL);
			Element sigTag = new Element(SIGNAL);
			Element typeTag = new Element(TYPE);
			Element hardTag = new Element(HARDWARE);
			Element switchTag = new Element(SWITCH);
			Element stateTag = new Element(STATE);
			Element commTag = new Element(COMMUNICATION);
			macTag.setText(mac);
			ele.addContent(macTag);
			ele.addContent(ipTag);
			ele.addContent(serialTag);
			ele.addContent(intervallTag);
			ele.addContent(sigTag);
			ele.addContent(typeTag);
			ele.addContent(switchTag);
			ele.addContent(stateTag);
			ele.addContent(commTag);
			ele.addContent(hardTag);
			ele.addContent(battTag);
			doc.getRootElement().addContent(ele);
			doc.getRootElement().setAttribute(TAG_COUNT, String.valueOf(macs.size()));
		}
	}

	private static void exportXML() {
		XMLOutputter out = new XMLOutputter();
		try (FileWriter writer = new FileWriter(xmlFile);) {
			out.output(doc, writer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void setState(String mac, String[] attrs, Object... values) {
		{
			if(macs.contains(mac)){
				int index = macs.indexOf(mac);
				Content content = doc.getRootElement().getContent(index);
				Element element = (Element)content;
				for(int x=0; x<attrs.length; x++){
					Element attrTag = element.getChild(attrs[x]);
					if (attrTag == null) {
						attrTag = new Element(attrs[x]);
						fixStateText(attrTag, attrs[x], values[x]);
						element.addContent(attrTag);
					} else {
						try {
							fixStateText(attrTag, attrs[x], values[x]);
						} catch (NullPointerException e1e) {
							System.out.println("e1:" + attrTag + " value:" + values[x]);
						}
					}
				}
			}
		}
		
	}

	private void fixStateText(Element attrTag, String attrs, Object value) {
		if(attrs.equals(HARDWARE)){
			if((boolean)value){
				attrTag.setText("error");
			}else{
				attrTag.setText("ok");
			}
		} else if (attrs.equals(SWITCH)){
			if((boolean)value){
				attrTag.setText("on");
			}else{
				attrTag.setText("off");
			}
			
		} else {
			attrTag.setText(value.toString());
		}
		
	}

	
}

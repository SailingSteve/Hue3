package main.java.com.podell;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Properties;

import nl.q42.jue.FullLight;
import nl.q42.jue.HueBridge;
import nl.q42.jue.Light;
import nl.q42.jue.State.AlertMode;
import nl.q42.jue.StateUpdate;
import nl.q42.jue.exceptions.ApiException;

import org.apache.http.client.ClientProtocolException;
import org.apache.commons.io.IOUtils;

/**
 * A small daemon to update hue colors based on jenkins status
 * @author stevepodell
 * https://github.com/Q42/Jue/wiki
 */
public class HueController {   
    private static Properties prop = null;
    private static HueBridge bridge = null;
	private static ArrayList<Lamp> arrayLamps = new ArrayList<Lamp>();   
	     
    public class Lamp {
    	String name;
    	Light light;
    	FullLight fullLight;
    	StateUpdate su;
    	String jenkinsColor;
    	String jenkinsJob;
    }
    

    // Constructor
    public HueController() {
    	try {
    		if( ! getPropValues() )
    			return;
          	bridge = new HueBridge((String)prop.getProperty("hueBridgeIP"));
          	bridge.authenticate("ffdcJenkins");
	    	String newUser = bridge.getUsername();
	    	
	    	System.out.println("Connected to the bridge as '" + newUser + "'");
	    	
 	    	// Initialize lamp array
	    	int i = 1;
			for (Light light : bridge.getLights()) {
				String job = (String)prop.getProperty("job"+ i++);
			    if( job == null )
			    	break;
			    Lamp lamp = new Lamp();
			    lamp.jenkinsJob = job;
				lamp.light = light;
			    lamp.fullLight = bridge.getLight(light);
			    lamp.name = lamp.fullLight.getName();
			    System.out.println(lamp.name + ", brightness: " + lamp.fullLight.getState().getBrightness() + ", hue: " + lamp.fullLight.getState().getHue() 
			    		+ ", saturation: " + lamp.fullLight.getState().getSaturation() + ", temperature: " + lamp.fullLight.getState().getColorTemperature());
			    arrayLamps.add(lamp);
			}				
    	} catch (IOException e) {
    		System.out.println("IoException 1 " + e );
		} catch (ApiException e) {
    		System.out.println("ApiException 1 " + e );
		}     	
    }

    // https://ci.dev.financialforce.com/api/xml
    // https://ci.dev.financialforce.com/view/PSA/api/json
    static boolean jenkinsStateUpdater() {
    	try {
			String json = getJenkinsDomCurl((String)prop.getProperty("urlString"), (String)prop.getProperty("username"), (String)prop.getProperty("password"));
			StringBuilder sb = new StringBuilder();
			for(Lamp lamp : arrayLamps) {
				lamp.jenkinsColor = getColor(json, lamp.jenkinsJob);
				lamp.su = buildStateUpdateForAJenkinsColor(lamp.jenkinsColor);
				bridge.setLightState(lamp.light, lamp.su);
		    	sb.append("'" +lamp.name + "' -> '" + lamp.jenkinsColor + "' (" + lamp.fullLight.getState().getBrightness() + ") for '" + 
		    			lamp.jenkinsJob + "';  ");
			}
	  		System.out.println( new SimpleDateFormat("MM/dd/yyyy HH:mm:ss - ").format(Calendar.getInstance().getTime()) + sb );
		} catch (ClientProtocolException e) {
	  		System.out.println("ClientProtocolException " + e );
		} catch (IOException e) {
	  		System.out.println("IoException 2 " + e );
		} catch (ApiException e) {
	  		System.out.println("ApiException 1 " + e );
		}
        
        return true;
    }
    
    static StateUpdate buildStateUpdateForAJenkinsColor( String jenkinsColor ) {
    	if(jenkinsColor.equals("blue")) 
     		return new StateUpdate().turnOn().setHue(Integer.valueOf(prop.getProperty("jenkins_blue_hue")))
     				.setBrightness(Integer.valueOf(prop.getProperty("jenkins_blue_brightness")))
     				.setAlert(AlertMode.NONE);
    	if(jenkinsColor.equals("red"))
     		return new StateUpdate().turnOn().setHue(Integer.valueOf(prop.getProperty("jenkins_red_hue")))
     				.setBrightness(Integer.valueOf(prop.getProperty("jenkins_red_brightness")))
     				.setAlert(AlertMode.NONE);    	
    	if(jenkinsColor.equals("blue_anime"))
     		return new StateUpdate().turnOn().setHue(Integer.valueOf(prop.getProperty("jenkins_blue_anime_hue")))
     				.setBrightness(Integer.valueOf(prop.getProperty("jenkins_blue_anime_brightness")))
     				.setAlert(AlertMode.SELECT);
    	if(jenkinsColor.equals("red_anime"))
     		return new StateUpdate().turnOn().setHue(Integer.valueOf(prop.getProperty("jenkins_red_anime_hue")))
     				.setBrightness(Integer.valueOf(prop.getProperty("jenkins_red_anime_brightness")))
     				.setAlert(AlertMode.SELECT);
    	// else "notbuilt" or "disabled", is dim white
 			return new StateUpdate().turnOn().setColorTemperature(Integer.valueOf(prop.getProperty("jenkins_disabled_color_temperature")))
 					.setBrightness(Integer.valueOf(prop.getProperty("jenkins_disabled_brightness")))
 					.setAlert(AlertMode.NONE);
    }
    
    /**
     * Another hack that avoids doing a dom parsing, just to find the color of the job
     * @param json
     * @param jobName
     * @return String, the color or null if not found
     */
    static String getColor(String json, String jobName) {
    	int nameIndex = json.indexOf(jobName);
    	if(nameIndex > 0) {
    		int colorIndex = json.indexOf("\"color\":\"", nameIndex) + 9; {
    			int endColorIndex = json.indexOf('"', colorIndex); {
    				if(endColorIndex > 0) {
    					String color = json.substring(colorIndex, endColorIndex);
    					//System.out.println("Color is: " + color);
    					return color;
    				}		
    			}
    		}
    	}
    	return null;
    }
     
	/**
	 * A hack to avoid spending hours on httpclient setup for a secure jenkins site
	 * $ curl -u spodell%40financialforce.com:myapitokene1eb6eb5566fe3173914444802b44056 https://ci.dev.financialforce.com/api/xml
	 * https://ci.dev.financialforce.com/view/PSA/api/json
	 * @param urlString
	 * @param username
	 * @param password
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static String getJenkinsDomCurl(String urlString, String username, String password) throws ClientProtocolException, IOException {
		String token = username + ":" + password;
	
	    ProcessBuilder pb = new ProcessBuilder(
	            "curl",
	            "-u",
	            token,
	            urlString);
	
	    // pb.redirectErrorStream(true);
	    Process p = pb.start(); 
	    String out = IOUtils.toString(p.getInputStream(), "UTF-8");
	    // System.out.println(out);
	    return out;
	}

	public static boolean getPropValues() throws IOException {
		prop = new Properties();
		
		InputStream in = HueController.class.getResourceAsStream("config.properties");
		if(in == null) {
			System.out.println("The config.properties file is not on the classpath!");
			return false;
		}
		prop.load(in);
		return true;
	}
    
    /**
     * Main  Hue Controller
     * https://github.com/SailingSteve/hue3.git
	 * Steve Podell
	 * @param args 0: <computer name>, 0|1: "classpath", if you want to print the classpath on startup
	 */
    public static void main(String[] args) {
   	
  		if( Arrays.asList(args).contains("classpath") )
	  		System.out.println( "Classpath: " + System.getProperty("java.class.path"));

  		new HueController();
	  	while( prop != null ) {
	  		jenkinsStateUpdater(); 
	  		try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {}
	  	}
    }  
}

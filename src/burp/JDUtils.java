/*
 * Copyright (c) John Murray, 2015.
 *
 *   This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package burp;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by John on 24/03/2015.
 */
public class JDUtils {

    private static XStream xstream = new XStream(new DomDriver());
    protected static ClassLoader loader;
//    private static final String LIB_DIR = "./libs/";
    private static final String LIB_DIR = "/Users/ahorn/burplibs";
    private static byte[] crap;
    public static byte[] serializeMagic = new byte[]{-84, -19};
    private static Object obj;
    public static String SERIALIZEHEADER = "Via:SERIALIZED-GOODNESS";

    public static byte[] fromXML(byte[] original, IExtensionHelpers helpers)
    {
        // xstream doen't like newlines
        String xml = helpers.bytesToString(original).replace("\n", "");

        // reserialize the data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

         try {
             try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                 xstream.setClassLoader(getSharedClassLoader()); //bugfix JM 2015/03/24
                 oos.writeObject(xstream.fromXML(xml));
                 oos.flush();
             }

         } catch (Exception ex) {
             System.out.println("Error deserializing from XML to Java object " + ex.getMessage());
         }


        byte[] baObj = baos.toByteArray();

        if (crap != null) //comes from a request, not a previously clicked tab
        {
            // reconstruct our message (add the crap buffer)
            byte[] newBody = new byte[baObj.length + crap.length];

            System.arraycopy(crap, 0, newBody, 0, crap.length);
            System.arraycopy(baObj, 0, newBody, crap.length, baObj.length);
        }

        return baObj;
    }

    public static byte[] toXML(byte[] plaintext, IExtensionHelpers helpers) throws IOException, ClassNotFoundException
    {
        CustomLoaderObjectInputStream is = null;

       // int magicPos = helpers.indexOf(plaintext, serializeMagic, false, 0, plaintext.length);
        int magicPos = JDUtils.indexOf(plaintext, serializeMagic);

        int msgBody = helpers.analyzeRequest(plaintext).getBodyOffset();

        // get serialized data
        byte[] baSer = Arrays.copyOfRange(plaintext, magicPos, plaintext.length);

        // save the crap buffer for reconstruction
        crap = Arrays.copyOfRange(plaintext, msgBody, magicPos);

        // deserialize the object
        ByteArrayInputStream bais = new ByteArrayInputStream(baSer);


        // Use a custom OIS that uses our own ClassLoader
        is = new CustomLoaderObjectInputStream(bais, JDUtils.getSharedClassLoader());
        obj = is.readObject();
        String xml = xstream.toXML(obj);

        try {
            is.close();
        } catch (Exception ex) {
                System.out.println("Error deserializing from Java object to XML  " + ex.getMessage());
        }

        return xml.getBytes();
    }

    public static ClassLoader getSharedClassLoader()
    {
        if(loader == null) {
            refreshSharedClassLoader();
        }
        return loader;
    }

    protected static ClassLoader createURLClassLoader(String libDir)
    {
        File dependencyDirectory = new File(libDir);
        File[] files = dependencyDirectory.listFiles();
        ArrayList<URL> urls = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(".jar")) {
                try {
                    System.out.println("Loading: " + files[i].getName());
                    urls.add(files[i].toURI().toURL());
                } catch (MalformedURLException ex) {
                    Logger.getLogger(BurpExtender.class.getName()).log(Level.SEVERE, null, ex);
                    System.out.println("!! Error loading: " + files[i].getName());
                }
            }
        }
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }

    public static void refreshSharedClassLoader()
    {
        loader = createURLClassLoader(LIB_DIR);
    }

    public static int indexOf(byte[] outerArray, byte[] smallerArray) {
        for(int i = 0; i < outerArray.length - smallerArray.length+1; ++i) {
            boolean found = true;
            for(int j = 0; j < smallerArray.length; ++j) {
               if (outerArray[i+j] != smallerArray[j]) {
                   found = false;
                   break;
               }
            }
            if (found) return i;
         }
       return -1;  
    }  

    public static boolean isJD(byte[] content, IExtensionHelpers helpers)
    {
       // return helpers.indexOf(content, JDUtils.serializeMagic, false, 0, content.length) > -1;
       return JDUtils.indexOf(content,JDUtils.serializeMagic ) > -1;
    }

    public static boolean hasMagicHeader(byte[] content, IExtensionHelpers helpers)
    {
        return helpers.indexOf(content, helpers.stringToBytes(JDUtils.SERIALIZEHEADER), false, 0, content.length) > -1;
    }

}


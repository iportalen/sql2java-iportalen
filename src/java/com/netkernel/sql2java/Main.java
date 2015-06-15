//$Id: Main.java,v 1.1 2005-10-20 19:43:22 fbr Exp $

package com.netkernel.sql2java;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

public class Main {

    private static Properties prop;

    public static void main(String argv[])
    {
        // Check for required argument
        if(argv == null || argv.length < 1) {
            System.err.println("Usage: java com.netkernel.generation.sql2code.Main <properties filename>");
            System.exit(1);
        }

        prop = new Properties();

        try
        {
            prop.load(new FileInputStream(argv[0]));
            Database db = new Database();
            db.setDriver(getProperty("jdbc.driver"));
            db.setURL(getProperty("jdbc.url"));
            db.setUsername(getProperty("jdbc.username"));
            db.setPassword(getProperty("jdbc.password"));
            db.setCatalog(getProperty("jdbc.catalog"));
            db.setSchema(getProperty("jdbc.schema"));
            db.setTableNamePattern(getProperty("jdbc.tablenamepattern"));

            String tt = getProperty("jdbc.tabletypes", "TABLE");
            StringTokenizer st = new StringTokenizer(tt, ",");
            List<String> al = new ArrayList<String>();

            while(st.hasMoreTokens()) {
                al.add(st.nextToken().trim());
            }

            db.setTableTypes(al.toArray(new String[al.size()]));
    
            db.load();

            PreparedManagerWriter writer = new PreparedManagerWriter();
            writer.setDatabase(db);
            writer.setProperties(prop);
            writer.process();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static String getProperty(String key)
    {
        String s = prop.getProperty(key);
        return s!=null?s.trim():s;
    }

    public static String getProperty(String key, String default_val)
    {
        return prop.getProperty(key, default_val);
    }
}

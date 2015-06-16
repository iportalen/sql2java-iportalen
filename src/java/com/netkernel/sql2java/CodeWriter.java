//$Id: CodeWriter.java,v 1.2 2010-12-05 16:39:23 fbr Exp $

package com.netkernel.sql2java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

public abstract class CodeWriter
{
    protected static final String MGR_SUFFIX  			= "Manager";
    protected static final String MGR_CLASS   			= "Manager";
    protected static final String ITERATOR_SUFFIX  		= "Iterator";
    protected static final String LISTENER_SUFFIX  		= "Listener";
    protected static final String BEAN_SUFFIX                   = "Bean";
    protected static final String KEY_SUFFIX                   = "Key";
    protected static final String COMPARATOR_SUFFIX             = "Comparator";
    protected static final String SPACE	   			= "    ";
    protected static final String IMPORT_BLOCK  		= "imports";
    protected static final String EXTENDS_BLOCK 		= "extends";
    protected static final String CLASS_BLOCK   		= "class";
    
    public static final String LINE_SEP = System.getProperty("line.separator");
    
    protected String mgrImports[];
    
    protected String basePackage;
    protected String destDir;
    
    // These are reset for each Java file written out
    protected Table table;
    protected UserCodeParser userCode;
    protected PrintWriter writer;
    protected String className;
    protected String classPrefix;    
    protected String pkg;
    
    //     /**
    //      * Determines whether to convert char(1) columns to booleans. Since SQL92
    //      * doesn't have a boolean datatype, folks frequently use char(1) as a
    //      * boolean flag, and it's nice to be able to use those columns as native
    //      * booleans in Java..
    //      */

    protected static String dateClassName;
    protected static String timeClassName;
    protected static String timestampClassName;
    
    protected Database db;
    protected Properties prop;            
    protected Hashtable<String, String> includeHash;
    protected Hashtable<String, String> excludeHash;
    protected Hashtable<String, String> cachingTables;
            
    /**
     * Sets the string to prepend to all generated classes.
     * <br>
     * This is useful if you're worried your classes may have namespace
     * collision with some reserved words or java.lang. classes.
     */    
    public void setClassPrefix(String prefix) { 
        this.classPrefix = prefix; 
    }
    
    public String getClassPrefix() { 
        return classPrefix; 
    }
    
    public void setDatabase(Database db) { 
        this.db  = db; 
    }
    
    public void setProperties(Properties prop) throws Exception
    {
        this.prop = prop;
        dateClassName = prop.getProperty("jdbc2java.date", "java.sql.Date");
        timeClassName = prop.getProperty("jdbc2java.time", "java.sql.Time");
        timestampClassName = prop.getProperty("jdbc2java.timestamp", "java.sql.Timestamp");

        // Set properties
        basePackage = prop.getProperty("mgrwriter.package");
        destDir	 = prop.getProperty("mgrwriter.destdir");
        setClassPrefix(prop.getProperty("mgrwriter.classprefix"));
        
        excludeHash = setHash(prop.getProperty("mgrwriter.exclude"));
        includeHash = setHash(prop.getProperty("mgrwriter.include"));
        cachingTables = setHash(prop.getProperty("mgrwriter.caching"));
        
        if(basePackage == null)
        {
            throw new Exception("Missing property: mgrwriter.package");
        }

        if(destDir == null)
        {
            throw new Exception("Missing property: mgrwriter.destdir");
        }
        
        File dir = new File(destDir);
        try { 
            dir.mkdirs(); 
        } catch (Exception e) {  
            // ignore
        }
        
        if(!dir.isDirectory() || !dir.canWrite())
        {
            throw new Exception("Cannot write to: " + destDir);
        }
    }
    
    private Hashtable<String, String> setHash(String str) {
    	Hashtable<String, String> result = null;
    	
        if (str != null && str.trim().length() != 0) {
        	result = new Hashtable<String, String>();
	        StringTokenizer st = new StringTokenizer(str);
	        while(st.hasMoreTokens()) {
	        	String val = st.nextToken().toLowerCase();
	            result.put(val, val);
	        }
        }
        return result;
    }

    public synchronized void process() throws Exception
    {
        writeClasses();
    }
    
    private void writeClasses() throws Exception
    {
        // Setup import string for Managers
        mgrImports = new String[3];
        mgrImports[0] = "java.util.ArrayList";
        mgrImports[1] = "java.sql.*";
        
        // Write Manager base class if it doesn't currently exist
        writeManagerBase();
        
        // Generate core and manager classes for all tables
        Table tables[] = db.getTables();
        for(int i = 0; i < tables.length; i++) {

            // See if this is in our exclude or include list
            if(includeHash != null) {
                if(includeHash.get(tables[i].getName().toLowerCase()) != null) {
                    writeTable(tables[i]);
                }
            } else if(excludeHash != null) {
                if(excludeHash.get(tables[i].getName().toLowerCase()) == null) {
                    writeTable(tables[i]);
                }
            } else  {
                writeTable(tables[i]);
            }
        }
    }
    
    /**
     * Write out Manager.java file. 
     * <br>
     * Ok, I admit that this is silly. This file is being written verbatim, and could easily just be
     * copied from a file on the filesystem (with the correct package name pre-pended). I couldn't
     * figure out a good way to do that without having the user hardcode the path to the default
     * Manager.java file in their sql2code.properties file..and that seemed lame.
     * <br>
     * Plus, I guess having this method here allows us to do smart things with this class in the
     * future, so what the heck..
     */
     private void writeManagerBase() throws Exception
    {
        // Resolve class and package
        className = MGR_CLASS;
        pkg = basePackage;
        
        // Create the directory to store the .java file if necessary
        String filename = resolveFilename(pkg, className);
        System.out.println("Generating " + filename);
        File file = new File(filename);
        (new File(file.getParent())).mkdirs();
        
        // Parse the existing .java file (if it exists)
        userCode = new UserCodeParser(filename);
        
        // Bail if this file already exists
        // if(!userCode.isNew()) return;
        
        // Open the PrintWriter
        writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filename)));
        
        // Start class
        indent(0, "// --------------------------------------------------------");
        indent(0, "// Generated by sql2code - http://sql2java.sourceforge.net/");
        indent(0, "// jdbc driver used at code generation time: " + db.getDriver());
        indent(0, "// ");
        indent(0, "// Please help us improve this tool by reporting:");
        indent(0, "//    problems,");
        indent(0, "//    suggestions,");
        indent(0, "//    feedbacks,");
        indent(0, "//    ideas,");
        indent(0, "//  to http://sourceforge.net/projects/sql2java/");
        indent(0, "// --------------------------------------------------------");
        indent(0, "");
        indent(0, "package " + pkg + ";");
        indent(0, "");
        indent(0, "import java.sql.*;");
        indent(0, "import java.io.PrintWriter;");
        indent(0, "import javax.sql.DataSource;");

        indent(0,userCode.getBlock(IMPORT_BLOCK));
                
        indent(0, "");
        indent(0, "/**");
        indent(0, " * The Manager provides connections and manages transactions transparently.");
        indent(0, " * <br>");
        indent(0, " * It is a singleton, you get its instance with the getInstance() method.");
        indent(0, " * All of the XxxxManager classes use the Manager to get database connections.");
        indent(0, " * Before doing any operation, you must pass either a");
        indent(0, " * datasource or a jdbc driver/url/username/password.");
        indent(0, " * You may extend it and use setInstance() method to make sure your");
        indent(0, " * implementation is used as a singleton.");
        indent(0, " */");
        indent(0, "public class " + MGR_CLASS);
        indent(0, userCode.getBlock(EXTENDS_BLOCK));        
        indent(0, "{");
        indent(1, "private static Manager manager_instance = new Manager();");
        indent(1, "private static InheritableThreadLocal<Connection> trans_conn = new InheritableThreadLocal<Connection>();");
        indent(1, "private static int transactionCount;");
        indent(0, "");
        indent(1, "private PrintWriter pw = new PrintWriter(System.out);");
        indent(1, "private DataSource ds = null;");        
        indent(1, "private String jdbc_driver = null;");
        indent(1, "private String jdbc_url = null;");
        indent(1, "private String jdbc_username = null;");
        indent(1, "private String jdbc_password = null;");
        indent(0, "");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Returns the manager singleton instance.");
        indent(1, " */");
        indent(1, "public static Manager getInstance()");
        indent(1, "{");
        indent(1, "    return manager_instance;");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Sets the datasource to be used by the manager.");
        indent(1, " * <br>");
        indent(1, " * A good datasource manages a pool of connections.");
        indent(1, " *");
        indent(1, " * @param ds the data source");
        indent(1, " */");
        indent(1, "public void setDataSource(DataSource ds)");
        indent(1, "{");
        indent(1, "    this.ds = ds;");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Loads the passed jdbc driver.");
        indent(1, " * <br>");
        indent(1, " * Only needed if the datasource is not set.");
        indent(1, " */");
        indent(1, "public void setJdbcDriver(String jdbc_driver) throws ClassNotFoundException, InstantiationException, IllegalAccessException");
        indent(1, "{");
        indent(1, "    this.jdbc_driver = jdbc_driver;");
        indent(1, "    Class.forName(jdbc_driver).newInstance();");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Sets the jdbc url.");
        indent(1, " * <br>");
        indent(1, " * Only needed if the datasource is not set.");
        indent(1, " */");
        indent(1, "public void setJdbcUrl(String jdbc_url)");
        indent(1, "{");
        indent(1, "    this.jdbc_url = jdbc_url;");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Sets the username used to access the database.");
        indent(1, " * <br>");
        indent(1, " * Only needed if the datasource is not set.");
        indent(1, " */");        
        indent(1, "public void setJdbcUsername(String jdbc_username)");
        indent(1, "{");
        indent(1, "    this.jdbc_username = jdbc_username;");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Sets the password used to access the database.");
        indent(1, " * <br>");
        indent(1, " * Only needed if the datasource is not set.");
        indent(1, " */");        
        indent(1, "public void setJdbcPassword(String jdbc_password)");
        indent(1, "{");
        indent(1, "    this.jdbc_password = jdbc_password;");
        indent(1, "}");

        indent(1, "/**");
        indent(1, " * Gets an auto commit connection.");
        indent(1, " * <br>");
        indent(1, " * Normally you do not need this method that much ;-)");
        indent(1, " *");
        indent(1, " * @return an auto commit connection");
        indent(1, " */");
        indent(1, "public Connection getConnection() throws SQLException {");
        indent(1, "    Connection tc = transactionCount == 0 ? null : trans_conn.get();");
        indent(1, "    if (tc != null) {");
        indent(1, "        return tc;");
        indent(1, "    }");
        indent(0, "");
        indent(1, "    if (ds!=null) {");
        indent(1, "        return ds.getConnection();");
        indent(1, "    } else if (jdbc_driver != null && jdbc_url != null && jdbc_username != null && jdbc_password != null) {");
        indent(1, "        return DriverManager.getConnection(jdbc_url, jdbc_username, jdbc_password);");
        indent(1, "    } else { ");
        indent(1, "        throw new IllegalStateException(\"Please set a datasource or a jdbc driver/url/username/password\");");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Releases the database connection.");
        indent(1, " * <br>");
        indent(1, " * Normally you should not need this method ;-)");
        indent(1, " */");
        indent(1, "public void releaseConnection(Connection c) {");
        indent(1, "    Connection tc = transactionCount == 0 ? null : trans_conn.get();");
        indent(1, "    if (tc != null) {");
        indent(1, "        return;");
        indent(1, "    }");
        indent(0, "");
        indent(1, "    try { ");
        indent(1, "        if (c != null) { ");
        indent(1, "            c.close();");
        indent(1, "        }");
        indent(1, "    } catch (SQLException x) { ");
        indent(1, "        log(\"Could not release the connection: \"+x.toString());");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        indent(0, "");        
        indent(1, "/**");
        indent(1, " * Initiates a database transaction.");
        indent(1, " * <br>");
        indent(1, " * When working within a transaction, you should invoke this method first.");
        indent(1, " * The connection is returned just in case you need to set the isolation level.");
        indent(1, " *");
        indent(1, " * @return a non-auto commit connection with the default transaction isolation level");
        indent(1, " */");
        indent(1, "public Connection beginTransaction() throws SQLException {");
        indent(1, "    Connection c = getConnection();");
        indent(1, "    c.setAutoCommit(false);");
        indent(1, "    transactionCount++;");
        indent(1, "    trans_conn.set(c);");
        indent(1, "    return c;");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Releases connection used for the transaction and performs a commit or rollback.");
        indent(1, " *");
        indent(1, " * @param commit tells whether this connection should be committed");
        indent(1, " *        true for commit(), false for rollback()");
        indent(1, " */");
        indent(1, "public void endTransaction(boolean commit) throws SQLException {");
        indent(1, "    Connection c = transactionCount == 0 ? null : trans_conn.get();");
        indent(1, "    if (c == null) {");
        indent(1, "        return;");
        indent(1, "    }");
        indent(0, "");
        indent(1, "    try { ");
        indent(1, "        if (commit) { ");
        indent(1, "            c.commit();");
        indent(1, "        } else {");
        indent(1, "            c.rollback();");
        indent(1, "        }");
        indent(1, "    } finally { ");
        indent(1, "        c.setAutoCommit(true);");
        indent(1, "        trans_conn.set(null);");
        indent(1, "        transactionCount--;");
        indent(1, "        releaseConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        indent(0, "");

        indent(1, "/**");
        indent(1, " * Sets the PrintWriter where logs are printed.");
        indent(1, " * <br>");
        indent(1, " * You may pass 'null' to disable logging.");
        indent(1, " *");
        indent(1, " * @param pw the PrintWriter for log messages");
        indent(1, " */");
        indent(1, "public void setLogWriter(PrintWriter pw) {");
        indent(1, "    this.pw = pw;");
        indent(1, "}");
        indent(0, "");
               
        indent(0, "");
        indent(0, "");        
        indent(0, "////////////////////////////////////////////////////");
        indent(0, "// Utils method");
        indent(0, "////////////////////////////////////////////////////");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Logs a message using the underlying logwriter, if not null.");
        indent(1, " */");
        indent(1, "public void log(String message) {");
        indent(1, "    if (pw!=null)");
        indent(1, "        pw.println(message);");
        indent(1, "}");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Closes the passed Statement.");
        indent(1, " */");
        indent(1, "public void close(Statement s) {");
        indent(1, "    try {");
        indent(1, "        if (s != null) s.close();");
        indent(1, "    } catch (SQLException x) {");
        indent(1, "        log(\"Could not close statement!: \" + x.toString());");
        indent(1, "    } ");
        indent(1, "}");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Closes the passed ResultSet.");
        indent(1, " */");
        indent(1, "public void close(ResultSet rs) {");
        indent(1, "    try {");
        indent(1, "        if (rs != null) rs.close();");
        indent(1, "    } catch (SQLException x) {");
        indent(1, "        log(\"Could not close result set!: \" + x.toString());");
        indent(1, "    } ");
        indent(1, "}");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Closes the passed Statement and ResultSet.");
        indent(1, " */");
        indent(1, "public void close(Statement s, ResultSet rs) {");
        indent(1, "    close(rs);");
        indent(1, "    close(s);");
        indent(1, "}");
        indent(1, "");
        indent(1, "");
        indent(1, "/**");
        indent(1, " * Retrieves an int value from the passed result set as an Integer object.");
        indent(1, " */");
        indent(1, "public static Integer getInteger(ResultSet rs, int pos) throws SQLException");
        indent(1, "{");
        indent(1, "    int i = rs.getInt(pos);");
        indent(1, "    return rs.wasNull() ? (Integer)null : new Integer(i);");
        indent(1, "}");
		indent(1, "");
		indent(1, "");
		indent(1, "/**");
		indent(1, " * Set an Integer object to the passed prepared statement as an int or as null.");
		indent(1, " */");
		indent(1, "public static void  setInteger(PreparedStatement ps, int pos, Integer i) throws SQLException");
		indent(1, "{");
		indent(1, "    if (i==null) {");
		indent(1, "        ps.setNull(pos, Types.INTEGER);");
		indent(1, "    } else {");
		indent(1, "        ps.setInt(pos, i.intValue());");
		indent(1, "    }");
		indent(1, "}");
        indent(1, "");
        indent(1, "");
        indent(1, "/**");
        indent(1, " * Retrieves a float value from the passed result set as a Float object.");
        indent(1, " */");
        indent(1, "public static Float getFloat(ResultSet rs, int pos) throws SQLException");
        indent(1, "{");
        indent(1, "    float f = rs.getFloat(pos);");
        indent(1, "    return rs.wasNull() ? (Float)null : new Float(f);");
        indent(1, "}");
        indent(1, "");
        indent(1, "");
		indent(1, "/**");
		indent(1, " * Set a Float object to the passed prepared statement as a float or as null.");
		indent(1, " */");
		indent(1, "public static void  setFloat(PreparedStatement ps, int pos, Float f) throws SQLException");
		indent(1, "{");
		indent(1, "    if (f==null) {");
		indent(1, "        ps.setNull(pos, Types.FLOAT);");
		indent(1, "    } else {");
		indent(1, "        ps.setFloat(pos, f.floatValue());");
		indent(1, "    }");
		indent(1, "}");
		indent(1, "");
		indent(1, "");
        indent(1, "/**");
        indent(1, " * Retrieves a double value from the passed result set as a Double object.");
        indent(1, " */");
        indent(1, "public static Double getDouble(ResultSet rs, int pos) throws SQLException");
        indent(1, "{");
        indent(1, "    double d = rs.getDouble(pos);");
        indent(1, "    return rs.wasNull() ? (Double)null : new Double(d);");
        indent(1, "}");
        indent(1, "");
        indent(1, "");
		indent(1, "/**");
		indent(1, " * Set a Double object to the passed prepared statement as a double or as null.");
		indent(1, " */");
		indent(1, "public static void  setDouble(PreparedStatement ps, int pos, Double d) throws SQLException");
		indent(1, "{");
		indent(1, "    if (d==null) {");
		indent(1, "        ps.setNull(pos, Types.DOUBLE);");
		indent(1, "    } else {");
		indent(1, "        ps.setDouble(pos, d.doubleValue());");
		indent(1, "    }");
		indent(1, "}");
		indent(1, "");
		indent(1, "");        
        indent(1, "/**");
        indent(1, " * Retrieves a long value from the passed result set as a Long object.");
        indent(1, " */");
        indent(1, "public static Long getLong(ResultSet rs, int pos) throws SQLException");
        indent(1, "{");
        indent(1, "    long l = rs.getLong(pos);");
        indent(1, "    return rs.wasNull() ? (Long)null : new Long(l);");
        indent(1, "}");
        indent(1, "");
        indent(1, "");
		indent(1, "/**");
		indent(1, " * Set a Long object to the passed prepared statement as a long or as null.");
		indent(1, " */");
		indent(1, "public static void  setLong(PreparedStatement ps, int pos, Long l) throws SQLException");
		indent(1, "{");
		indent(1, "    if (l==null) {");
		indent(1, "        ps.setNull(pos, Types.BIGINT);");
		indent(1, "    } else {");
		indent(1, "        ps.setLong(pos, l.longValue());");
		indent(1, "    }");
		indent(1, "}");
		indent(1, "");
		indent(1, "");
        indent(1, "/**");
        indent(1, " * Retrieves a boolean value from the passed result set as a Boolean object.");
        indent(1, " */");
        indent(1, "public static Boolean getBoolean(ResultSet rs, int pos) throws SQLException");
        indent(1, "{");
        indent(1, "    boolean b = rs.getBoolean(pos);");
        indent(1, "    return rs.wasNull() ? (Boolean)null : Boolean.valueOf(b);");
        indent(1, "}");
		indent(1, "/**");
		indent(1, " * Set a Boolean object to the passed prepared statement as a boolean or as null.");
		indent(1, " */");
		indent(1, "public static void  setBoolean(PreparedStatement ps, int pos, Boolean b) throws SQLException");
		indent(1, "{");
		indent(1, "    if (b==null) {");
		indent(1, "        ps.setNull(pos, Types.BOOLEAN);");
		indent(1, "    } else {");
		indent(1, "        ps.setBoolean(pos, b.booleanValue());");
		indent(1, "    }");
		indent(1, "}");
		indent(1, "");
		indent(1, "");

        indent(0, "");
        // end class
        writeEnd();
    }
    
    private void writeTable(Table table) throws Exception
    {
        if (table.getColumns().length == 0) {
            System.out.println("WARNING: No column found in table "+ table.getName() + ". Skipping code generation for this table.");
            return;
        }

        this.table = table;
        writeCore();
        writeManager();
        writeComparator();
        if (table.getPrimaryKeys().length > 0) {
        	writeKey();
        }
        writeListener();
//        writeManagerImplementation();
    }
    
    protected void writeManager() throws Exception { }
    protected void writeRelationshipManager() throws Exception { }

    // ################################################################
    // COMPARATOR 
    // #################################################################
                
    private void writeComparator() throws Exception
    {
        Column columns[] = table.getColumns();
        String coreClass = generateCoreClassName();
        String beanClass = generateBeanClassName();
        
        className = generateComparatorClassName();
        pkg = basePackage;// + "." + convertName(table.getName()).toLowerCase();
        // Setup the PrintWriter and read in existing Java source file
        initWriter();
        indent(0, "package " + pkg + ";");
        writer.println();
        indent(0, "import java.util.Comparator;");
        indent(0, userCode.getBlock(IMPORT_BLOCK));
        
        writer.println();
        indent(0, "/**");
        indent(0, " * Comparator class is used to sort the " + beanClass + " objects.");
        indent(0, " */");
        indent(0, "public class " + className + " implements Comparator<"+beanClass+">");
        writer.println(userCode.getBlock(EXTENDS_BLOCK));
        indent(0, "{");

        indent(1, "/**");
        indent(1, " * Holds the field on which the comparison is performed.");
        indent(1, " */");
        indent(1, "private int iType;");
        indent(1, "/**");
        indent(1, " * Value that will contain the information about the order of the sort: normal or reversal.");
        indent(1, " */");
        indent(1, "private boolean bReverse;");
        indent(0, "");

        // simple constructor
        indent(1, "/**");
        indent(1, " * Constructor class for " + className + ".");
        indent(1, " * <br>");
        indent(1, " * Example:");
        indent(1, " * <br>");
        indent(1, " * <code>Arrays.sort(pArray, new " + className + "(" + coreClass + "Manager." + columns[0].getConstName() + ", bReverse));<code>");
        indent(1, " *");
        indent(1, " * @param iType the field from which you want to sort");
        indent(1, " * <br>");
        indent(1, " * Possible values are:");
        indent(1, " * <ul>");
        for(int i = 0; i < columns.length; i++) {
            if(columns[i].hasCompareTo()) {
                indent(1, " *   <li>" + generateManagerClassName() + ".ID_" + columns[i].getConstName());
            }
        }
        indent(1, " * </ul>");
        indent(1, " */");
        indent(1, "public " + className + "(int iType)");
        indent(1, "{");
        indent(1, "    this(iType, false);");
        indent(1, "}");
        indent(0, "");
        
        // constructor
        indent(1, "/**");
        indent(1, " * Constructor class for " + className + ".");
        indent(1, " * <br>");
        indent(1, " * Example:");
        indent(1, " * <br>");
        indent(1, " * <code>Arrays.sort(pArray, new " + className + "(" + coreClass + "Manager." + columns[0].getConstName() + ", bReverse));<code>");
        indent(1, " *");
        indent(1, " * @param iType the field from which you want to sort.");
        indent(1, " * <br>");
        indent(1, " * Possible values are:");
        indent(1, " * <ul>");
        for(int i = 0; i < columns.length; i++) {
            indent(1, " *   <li>" + generateManagerClassName() + ".ID_" + columns[i].getConstName());
        }
        indent(1, " * </ul>");
        indent(1, " *");
        indent(1, " * @param bReverse set this value to true, if you want to reverse the sorting results");
        indent(1, " */");
        indent(1, "public " + className + "(int iType, boolean bReverse)");
        indent(1, "{");
        indent(1, "    this.iType = iType;");
        indent(1, "    this.bReverse = bReverse;");
        indent(1, "}");
        indent(0, "");
        
        // compare
        indent(1, "/**");
        indent(1, " * Implementation of the compare method.");
        indent(1, " */");
        indent(1, "public int compare("+beanClass+" b1, "+beanClass+" b2)");
        indent(1, "{");
        indent(1, "    int iReturn = 0;");
        indent(1, "    switch(iType)");
        indent(1, "    {");
        for(int i = 0; i < columns.length; i++)
        {
            try
            {
                if (columns[i].hasCompareTo())
                {
                    indent(1, "        case "  + generateManagerClassName() + ".ID_" + columns[i].getConstName() + ":");
                    indent(1, "            if (b1."  + getGetMethod(columns[i])
                    + "() == null && b2." + getGetMethod(columns[i]) + "() != null) {");
                    indent(1, "                iReturn = -1;");
                    indent(1, "            } else if (b1."  + getGetMethod(columns[i])
                    + "() == null && b2." + getGetMethod(columns[i]) + "() == null) {");
                    indent(1, "                iReturn = 0;");
                    indent(1, "            } else if (b1."  + getGetMethod(columns[i])
                    + "() != null && b2." + getGetMethod(columns[i]) + "() == null) {");
                    indent(1, "                iReturn = 1;");
                    indent(1, "            } else { ");
                    indent(1, "                iReturn = b1."  + getGetMethod(columns[i]) +
                           "().compareTo(b2."  + getGetMethod(columns[i]) + "());");
                    indent(1, "            }");
                    indent(1, "            break;");
                }
            }
            catch(Exception e)
            {
                System.out.println(e.toString());
            }
        }
        indent(1, "        default: ");
        indent(1, "            throw new IllegalArgumentException(\"Type passed for the field is not supported\");");
        indent(1, "    }");
        indent(0, "");
        indent(1, "    return bReverse ? (-1 * iReturn) : iReturn;");
        indent(1, "}");
                        
        writeEnd();
    }

    // #################################################################
    // LISTENER
    // #################################################################            
    private void writeListener() throws Exception
    {        
        // Resolve class and package
        className = generateListenerClassName();
        String beanName =  generateBeanClassName();
        pkg = basePackage;// + "." + convertName(table.getName()).toLowerCase();
        
        // Setup the PrintWriter and read in existing Java source file
        initWriter();
        indent(0, "package " + pkg + ";");
        indent(0, "");
        indent(0, "import java.sql.SQLException;");

        indent(0,userCode.getBlock(IMPORT_BLOCK));        
        indent(0, "");
        indent(0, "");
        indent(0, "/**");
        indent(0, " * Listener that is notified of " + table.getName() + " table changes.");
        indent(0, " */");        
        indent(0, "public interface " + className);
        indent(0,userCode.getBlock(EXTENDS_BLOCK));        
        indent(0, "{");

        indent(1, "/**");
        indent(1, " * Invoked just before inserting a " + beanName + " record into the database.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanName + " that is about to be inserted");
        indent(1, " */");
        indent(1,"public void beforeInsert("+beanName+" pObject) throws SQLException;");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Invoked just after a " + beanName + " record is inserted in the database.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanName + " that was just inserted");
        indent(1, " */");
        indent(1,"public void afterInsert("+beanName+" pObject) throws SQLException;");
        indent(0, "");
        indent(0, "");

        indent(1, "/**");
        indent(1, " * Invoked just before updating a " + beanName + " record in the database.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanName + " that is about to be updated");
        indent(1, " */");
        indent(1,"public void beforeUpdate("+beanName+" pObject) throws SQLException;");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Invoked just after updating a " + beanName + " record in the database.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanName + " that was just updated");
        indent(1, " */");
        indent(1,"public void afterUpdate("+beanName+" pObject) throws SQLException;");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Invoked just before deleting one or more " + beanName + " record(s) in the database.");
        indent(1, " *");
        indent(1, " * @param the " + beanName + "(s) that is going to be deleted");
        indent(1, " */");
        indent(1,"public void beforeDelete("+beanName+"[] beans) throws SQLException;");
        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Invoked just after deleting one or more " + beanName + " record(s) in the database.");
        indent(1, " *");
        indent(1, " * @param the " + beanName + "(s) that was just deleted");
        indent(1, " */");
        indent(1,"public void afterDelete("+beanName+"[] deletedBeans) throws SQLException;");
        indent(0, "");
        indent(0, "");

        // End class
        writeEnd();
    }
    
    // #################################################################
    // LISTENER
    // #################################################################            
    private void writeKey() throws Exception
    {        
        // Resolve class and package
        className = generateKeyClassName();
        pkg = basePackage;// + "." + convertName(table.getName()).toLowerCase();
        
        // Setup the PrintWriter and read in existing Java source file
        initWriter();
        indent(0, "package " + pkg + ";");
        indent(0, "");

        indent(0,userCode.getBlock(IMPORT_BLOCK));        
        indent(0, "");
        indent(0, "");
        indent(0, "/**");
        indent(0, " * Key class to this table " + table.getName());
        indent(0, " */");        
        indent(0, "public class " + className);
        indent(0,userCode.getBlock(EXTENDS_BLOCK));        
        indent(0, "{");
        
        // Add all the instance variables from the key
        indent(1, "// Private members");
        Column[] columns = table.getPrimaryKeys();
        for (Column column : columns) {
            indent(1, "private " + column.getJavaType() + " " + getVarName(column)+";");
        }
        indent(0, "");
        
        // Write the constructors
		indent(1, "/**");
		indent(1, " * Constructor for " + className);
		indent(1, " * This class is imutable");
		indent(1, " */");

        indent(1, "public " + className+"("+getMethodParameters(columns)+") {");
        for (Column column : columns) {
            indent(2, "this." + getVarName(column)+" = " + getVarName(column)+";");
        }
        indent(1, "}");
        
        // Write the getters
        indent(0, "");
        for (Column column : columns) {
            writeGetterMethod(column);
        }
        
        writeToStringMethod(columns);
        writeHashCode(columns);
        writeEquals(columns, generateKeyClassName());
        
        // End class
        writeEnd();
    }

    private void writeCore() throws Exception
    {
        Column columns[] = table.getColumns();
        
        // Resolve class and package
        className = generateBeanClassName();
        pkg = basePackage;// + "." + convertName(table.getName()).toLowerCase();
        
        // Setup the PrintWriter and read in existing Java source file
        initWriter();
        
        //String coreClass = generateCoreClassName();
        String beanClass = generateBeanClassName();
                
        // Start class
        writePreamble();

        // Write out instance variables (one per column)
        for(int i = 0; i < columns.length; i++)
        {
            indent(1, "private " + columns[i].getJavaType() + " " + getVarName(columns[i]) + getDefaultValue(columns[i]));
            indent(1, "private boolean " + getModifiedVarName(columns[i]) + " = false;");
            indent(1, "private boolean " + getInitializedVarName(columns[i]) + " = false;");
            indent(1, "");
        }
        
        indent(1, "private boolean _isNew = true;");
        indent(1, "");
 
        indent(1, "/**");
        indent(1, " * Do not use this constructor directly, please use the factory method");
        indent(1, " * available in the associated manager.");
        indent(1, " */");
        indent(1, "" + className + "()");
        indent(1, "{");
        indent(1, "}");
        indent(1, "");

        // Write out get/set methods
        for(int i = 0; i < columns.length; i++)
        {
            writeGetterMethod(columns[i]);
            
            // Set Method
            indent(1, "/**");
            indent(1, " * Setter method for " + getVarName(columns[i]) + ".");
            indent(1, " * <br>");
            if (columns[i].hasCompareTo())
            {
                indent(1, " * The new value is set only if compareTo() says it is different,");
                indent(1, " * or if one of either the new value or the current value is null.");
                indent(1, " * In case the new value is different, it is set and the field is marked as 'modified'.");
            } else {
                indent(1, " * Attention, there will be no comparison with current value which");
                indent(1, " * means calling this method will mark the field as 'modified' in all cases.");
            }
            indent(1, " *");
            indent(1, " * @param newVal the new value to be assigned to " + getVarName(columns[i]));
            indent(1, " */");
            indent(1, "public void " + getSetMethod(columns[i]) + "(" + columns[i].getJavaType() + " newVal) {");
            // is equal ?
            if (columns[i].hasCompareTo())
            {
                indent(1, "    if ((newVal != null && this." + getVarName(columns[i]) + " != null && (newVal.compareTo(this." + getVarName(columns[i]) +") == 0)) || ");
                indent(1, "        (newVal == null && this." + getVarName(columns[i]) +" == null && "+ getInitializedVarName(columns[i])+")) {");
                indent(1, "        return; ");
                indent(1, "    } ");
            } else if (columns[i].useEqualsInSetter()) {
//                indent(1, "    if ((newVal != null && this." + getVarName(columns[i]) + " != null && newVal.equals(this." + getVarName(columns[i]) +")) || ");
//                indent(1, "        (newVal == null && this." + getVarName(columns[i]) +" == null && "+ getInitializedVarName(columns[i])+")) {");
//                indent(1, "        return; ");
//                indent(1, "    } ");
            }
            
            indent(1, "    this." + getVarName(columns[i]) +" = newVal; ");
            indent(1, "    " + getModifiedVarName(columns[i]) + " = true; ");
            indent(1, "    " + getInitializedVarName(columns[i]) + " = true; ");
            indent(1, "}");
            indent(0, "");

            //
            // Convenient setter for those who do not want to deal with Objects for primary types. 
            //
            try {
                String primType = columns[i].getJavaPrimaryType(); // throws illegal arg exception if not available
                // Set Method
                indent(1, "/**");
                indent(1, " * Setter method for " + getVarName(columns[i]) + ".");
                indent(1, " * <br>");
                indent(1, " * Convenient for those who do not want to deal with Objects for primary types.");
                indent(1, " *");
                indent(1, " * @param newVal the new value to be assigned to " + getVarName(columns[i]));
                indent(1, " */");
                indent(1, "public void " + getSetMethod(columns[i]) + "(" + primType+ " newVal) {");
                if (columns[i].getMappedType() == Column.M_BOOLEAN) {
                	indent(1, "    " + getSetMethod(columns[i]) + "(Boolean.valueOf(newVal));");                	
                } else {
                	indent(1, "    " + getSetMethod(columns[i]) + "(new " + columns[i].getJavaType() + "(newVal));");                	
                }
                indent(1, "}");
                indent(0, "");
            } catch (IllegalArgumentException iae) {
                // skipping non primary type
            }

            // Modified Method
            indent(1, "/**");
            indent(1, " * Determines if the " + getVarName(columns[i]) + " has been modified.");
            indent(1, " *");
            indent(1, " * @return true if the field has been modified, false if the field has not been modified");
            indent(1, " */");
            indent(1, "public boolean " + getModifiedMethod(columns[i]) +"() {");
            indent(1, "    return " + getModifiedVarName(columns[i]) + "; ");
            indent(1, "}");
            indent(0, "");
            
            // initialized Method
            indent(1, "/**");
            indent(1, " * Determines if the " + getVarName(columns[i]) + " has been initialized.");
            indent(1, " * <br>");
            indent(1, " * It is useful to determine if a field is null on purpose or just because it has not been initialized.");
            indent(1, " *");
            indent(1, " * @return true if the field has been initialized, false otherwise");
            indent(1, " */");
            indent(1, "public boolean " + getInitializedMethod(columns[i]) +"() {");
            indent(1, "    return " + getInitializedVarName(columns[i]) + "; ");
            indent(1, "}");
            indent(0, "");
        }
        
        // isNew
        indent(1, "/**");
        indent(1, " * Determines if the current object is new.");
        indent(1, " *");
        indent(1, " * @return true if the current object is new, false if the object is not new");
        indent(1, " */");
        indent(1, "public boolean isNew() {");
        indent(1, "    return _isNew;");
        indent(1, "}");
        indent(0, "");
        
        // setIsNew
        indent(1, "/**");
        indent(1, " * Specifies to the object if it has been set as new.");
        indent(1, " *");
        indent(1, " * @param isNew the boolean value to be assigned to the isNew field");
        indent(1, " */");
        indent(1, "public void isNew(boolean isNew) {");
        indent(1, "    this._isNew = isNew;");
        indent(1, "}");
        indent(0, "");
        
        // isModified
        indent(1, "/**");
        indent(1, " * Determines if the object has been modified since the last time this method was called.");
        indent(1, " * <br>");
        indent(1, " * We can also determine if this object has ever been modified since its creation.");
        indent(1, " *");
        indent(1, " * @return true if the object has been modified, false if the object has not been modified");
        indent(1, " */");
        indent(1, "public boolean isModified() {");
        StringBuffer fields = new StringBuffer("return ");
        for(int i = 0; i < columns.length; i++)
        {
            if(i > 0)
                fields.append(" || ").append(LINE_SEP).append("		");
            fields.append(getModifiedVarName(columns[i]));
        }
        fields.append(";");
        indent(2, fields.toString());
        indent(1, "}");
        indent(0, "");
        
        // resetIsModified
        indent(1, "/**");
        indent(1, " * Resets the object modification status to 'not modified'.");
        indent(1, " */");
        indent(1, "public void resetIsModified() {");
        for(int i = 0; i < columns.length; i++)
        {
            indent(2, getModifiedVarName(columns[i]) + " = false;");
        }
        indent(1, "}");
        indent(0, "");
        
        // copyInto
        indent(1, "/**");
        indent(1, " * Copies the passed bean into the current bean.");
        indent(1, " *");
        indent(1, " * @param bean the bean to copy into the current bean");
        indent(1, " */");
        indent(1, "public void copy(" + beanClass + " bean) {");
        for(int i = 0; i < columns.length; i++)
        {
            indent(2, getSetMethod(columns[i]) + "(bean." + getGetMethod(columns[i]) + "());");
        }
        indent(1, "}");
        indent(0, "");
        
		// toString
        indent(1, "/**");
		indent(1, " * Returns the object string representation.");
		indent(1, " *");
		indent(1, " * @return the object as a string");
		indent(1, " */");
		indent(1, "public String toString() {");
		indent(1, "    return   \"\\n[" + table.getName() + "] \"");
		for(int i = 0; i < columns.length; i++)
        {
            indent(1, "             + \"\\n - " + columns[i].getFullName() + " = \" + (" + getInitializedVarName(columns[i]) + " ? (\"[\" + (" + getVarName(columns[i]) + " == null ? null : "+ getVarName(columns[i]) + ".toString()) + \"]\") : \"not initialized\") + \"\"");
        }
		indent(1, "        ;");
		indent(1, "}");
		indent(0, "");
        
		if (table.getPrimaryKeys().length != 0) {
			writeGetKey(table.getPrimaryKeys());
			writeHashCode(table.getPrimaryKeys());
			writeEquals(table.getPrimaryKeys(), generateBeanClassName());
		}
        // End class
        writeEnd();
    }

	/**
	 * @param columns
	 */
	private void writeToStringMethod(Column[] columns) {
		// toString
        indent(1, "/**");
		indent(1, " * Returns the object string representation.");
		indent(1, " *");
		indent(1, " * @return the object as a string");
		indent(1, " */");
		indent(1, "public String toString() {");
		indent(1, "    return   \"\\n[" + table.getName() + "] \"");
		for(int i = 0; i < columns.length; i++)
        {
            indent(1, "             + \"\\n - " + columns[i].getFullName() + " = \" + " + getVarName(columns[i]));
        }
		indent(1, "        ;");
		indent(1, "}");
		indent(0, "");
	}

	/**
	 * @param columns
	 * @param i
	 */
	private void writeGetterMethod(Column column) {
		// Get Method
		indent(1, "/**");
		indent(1, " * Getter method for " + getVarName(column) + ".");
		indent(1, " * <br>");
		if (column.isPrimaryKey())
		    indent(1, " * PRIMARY KEY.<br>");
		indent(1, " * Meta Data Information (in progress):");
		indent(1, " * <ul>");
		indent(1, " * <li>full name: " + column.getFullName());
		if (column.getPointsTo()!=null)
		    indent(1, " * <li> foreign key: " + column.getPointsTo().getTableName() + 
		           "." + column.getPointsTo().getName());
		if (column.getRemarks()!=null && column.getRemarks().length()>0)        
		    indent(1, " * <li>comments: " + column.getRemarks());
		if (column.getDefaultValue()!=null && column.getDefaultValue().length()>0)                        
		    indent(1, " * <li>default value: " + column.getDefaultValue());
		indent(1, " * <li>column size: " + column.getSize());
		indent(1, " * <li>jdbc type returned by the driver: " + column.getJavaTypeAsTypeName());
		indent(1, " * </ul>");
		indent(1, " *");
		indent(1, " * @return the value of " + getVarName(column));
		indent(1, " */");
		indent(1, "public " + column.getJavaType() + " " + getGetMethod(column) + "()");
		indent(1, "{");
		indent(1, "    return " + getVarName(column) + "; ");
		indent(1, "}");
		indent(0, "");
	}
    
    // from method
    protected String getGetMethod(Column col) {
        return convertName("get_" + col.getName(), true);
    }
    
    protected String getSetMethod(Column col) {
        return convertName("set_" + col.getName(), true);
    }
    
    protected String getModifiedMethod(Column col) {
        return convertName("is_" + col.getName() + "_modified", true);
    }
    
    protected String getInitializedMethod(Column col) {
        return convertName("is_" + col.getName() + "_initialized", true);
    }
    
    // from name
    protected String getGetMethod(String  col) {
        return convertName("get_" + col, true);
    }
    
    protected String getSetMethod(String  col) {
        return convertName("set_" + col, true);
    }
    
    protected String getModifiedMethod(String  col) {
        return convertName("is_" + col + "_modified", true);
    }
    
    protected String getInitializedMethod(String col) {
        return convertName("is_" + col + "_initialized", true);
    }
   
    protected void initWriter() throws Exception {
        // Create the directory to store the .java file if necessary
        String filename = resolveFilename(pkg, className);
        System.out.println("Generating " + filename);
        File file = new File(filename);
        (new File(file.getParent())).mkdirs();
        
        // Parse the existing .java file (if it exists)
        userCode = new UserCodeParser(filename);
        
        // Open the PrintWriter
        writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filename)));
        writer.println("// --------------------------------------------------------");
        writer.println("// Generated by sql2java - http://sql2java.sourceforge.net/");
        writer.println("// jdbc driver used at code generation time: " + db.getDriver());
        writer.println("// ");
        writer.println("// Please help us improve this tool by reporting:");
        writer.println("//    problems,");
        writer.println("//    suggestions,");
        writer.println("//    feedbacks,");
        writer.println("//    ideas,");
        writer.println("//  to http://sourceforge.net/projects/sql2java/");
        writer.println("// --------------------------------------------------------");
        writer.println("");
    }
    
    private String getDefaultValue(Column column) {
    	if (column.getMappedType() == Column.M_BOOLEAN && column.getNullable() == 0) {
    		return "1".equals(column.getDefaultValue()) ? " = Boolean.TRUE;" : " = Boolean.FALSE;";
    	}
    	return ";";
    }
    
//    private String getDefaultModifiedValue(Column column) {
//    	if (column.getMappedType() == Column.M_BOOLEAN && column.getNullable() == 0) {
//    		return "true;";
//    	}
//    	return "false;";
//    }
    
    private void writePreamble() throws Exception {
        writePreamble(new String[0], null, null);
    }
    
    private void writePreamble(String importList[], String extendsClass, String implementsList[]) throws Exception {
        writer.println("package " + pkg + ";");
        writer.println();
        //writer.println("import java.util.*;");

        for(int i = 0; i < importList.length; i++)
        {
            writer.print("import ");
            writer.print(importList[i]);
            writer.println(";");
        }
        writer.println(userCode.getBlock(IMPORT_BLOCK));
        writer.println();
        writer.print("public class " + className);
        if(extendsClass != null)
            writer.print(" extends " + extendsClass);
        if (implementsList != null && implementsList.length != 0)
        {
            writer.print(" implements ");
            for(int i = 0; i < implementsList.length; i++)
            {
                if (i > 0)
                    writer.print(",");
                writer.print(implementsList[i]);
            }
        }
        writer.println();
        writer.println(userCode.getBlock(EXTENDS_BLOCK));
        writer.println("{");
    }
    
    protected void writeEnd() {
    	writer.println(userCode.getBlock(CLASS_BLOCK));
        writer.println("}");
        writer.close();
    }
    
    protected void indent(int tabs, String line) {
        for(int i = 0; i < tabs; i++)
            writer.print(SPACE);
        writer.println(line);
    }
    
    private void writeHashCode(Column[] columns) {
    	indent(1, "/* (non-Javadoc)");
    	indent(1, " * @see java.lang.Object#hashCode()");
    	indent(1, " * hashCode algorithm taken from Effective Java");
    	indent(1, " */");
    	indent(1, "public int hashCode() {");
    	indent(2, "int result = 17;");
    	for (Column column : columns) {
    		String hashCode = getVarName(column);
    		if (column.isColumnNumeric() == false) {
    			hashCode+=".hashCode()";
    		}
    		indent(2, "result = 37*result + " +  hashCode + ";");
    	}
    	indent(2, "return result;");
		indent(1, "}");
		indent(0, "");
    }
    
    private void writeEquals(Column[] columns, String className) {
    	indent(1, "/* (non-Javadoc)");
    	indent(1, " * @see java.lang.Object#equals(java.lang.Object)");
    	indent(1, " */");
    	indent(1, "public boolean equals(Object obj) {");
    	indent(2, "if (obj == this) {");
    	indent(2, "  return true;");
    	indent(2, "}");
    	indent(1, "");
    	indent(2, "if (obj instanceof " + className + " == false) {");
    	indent(2, "  return false;");
    	indent(2, "}");
    	indent(2, className + " other = (" + className +") obj;");
    	boolean first = true;
    	for (Column column : columns) {
    		String prefix = "&& ";
    		if (first == true) {
    			prefix ="return ";
    		}
    		String equals = getVarName(column);
   			equals+=".equals(other." + getVarName(column) + ")";
    		indent(2, prefix + equals);
    		first = false;
    	}
    	indent(2, ";");
    	indent(1, "}");
    	indent(0, "");
    }
    
    private void writeGetKey(Column[] columns) {
    	indent(1, "/**");
    	indent(1, " * Returns this beans primary key class");
    	indent(1, " * @return " + generateKeyClassName());  
    	indent(1, " */");
    	indent(1, "public " + generateKeyClassName() + " getKey() {");
    	indent(2, "return new " + generateKeyClassName() + "("+getCallerArguments(columns)+");");
    	indent(1, "}");
    	indent(0, "");
    	
    }

	/**
	 * Based on the columns this methods generates an method parameter signature. 
	 * Ex. Column BREED and SIZE will be converted to: String breed, Integer size
	 * @return
	 */
	protected String getMethodParameters(Column[] columns) {
		String parameters = "";
		boolean first = true;
		for (Column column : columns) {
        	if (first == false) {
        		parameters += ", ";
        	}
            parameters+= column.getJavaType() + " " + getVarName(column);
            first = false;
        }
		return parameters;
	}
    
	/**
	 * Based on the columns this methods generates an caller argument string. 
	 * Ex. Column BREED and SIZE will be converted to: breed, size
	 * @return
	 */
	protected String getCallerArguments(Column[] columns) {
		String arguments = "";
		boolean first = true;
		for (Column column : columns) {
    		if (first == false) {
    			arguments += ", ";
    		}
    		arguments += getVarName(column);
    		first = false;
    	}
		return arguments;
	}
    /**
     * Converts the package name and className into a full path.
     * <br>
     * This is accomplished by using the relative destination directory
     * as the base directory.
     */
	protected String resolveFilename(String pkg, String className) {
        String file = pkg + "." + className;
        return destDir + File.separatorChar + file.replace('.', File.separatorChar) + ".java";
    }
    
    protected String getVarName(Column c) {
        return convertName(c.getName(), true);
    }
    
    protected String getModifiedVarName(Column c) {
        return getVarName(c) + "_is_modified";
    }
    
    protected String getInitializedVarName(Column c) {
        return getVarName(c) + "_is_initialized";
    }
    
    protected String convertName(String name) {
        return convertName(name, false);
    }
    
    /**
     * Converts name into a more Java-ish style name. 
     * <br>
     * Basically it looks for underscores, removes them, and makes the
     * letter after the underscore a capital letter. If wimpyCaps is true,
     * then the first letter of the name will be lowercase, otherwise it
     * will be uppercase. Here are some examples:
     * <p>
     * member_profile   becomes   MemberProfile
     * <br>
     * firstname&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp becomes&nbsp Firstname
     */
    protected String convertName(String name, boolean wimpyCaps)
    {
        StringBuffer buffer = new StringBuffer(name.length());
        char list[] = name.toLowerCase().toCharArray();
        for(int i = 0; i < list.length; i++)
        {
            if(i == 0 && !wimpyCaps)
            {
                buffer.append(Character.toUpperCase(list[i]));
            }
            else if(list[i] == '_' && (i+1) < list.length && i != 0)
            {
                buffer.append(Character.toUpperCase(list[++i]));
            }
            else buffer.append(list[i]);
        }
        
        return buffer.toString();
    }
    
    protected String generateCoreClassName() {
        return generateCoreClassName(table.getName());
    }
    
    protected String generateCoreClassName(String strTableName) {
    	String result = null;
    	
    	if(classPrefix != null) {
            result =  classPrefix + convertName(strTableName);
    	} else {
    		result = convertName(strTableName);
    	}
    	return result;
    }
    
    protected String generateBeanClassName() {
        return generateCoreClassName(table.getName()) + BEAN_SUFFIX;
    }
    
    protected String generateKeyClassName() {
        return generateCoreClassName(table.getName()) + KEY_SUFFIX;
    }
    
    protected String generateBeanClassName(String strTableName) {
        return generateCoreClassName(strTableName) + BEAN_SUFFIX;
    }
    
    protected String generateManagerClassName() {
        return generateCoreClassName() + MGR_SUFFIX;
    }
    protected String generateManagerClassName(String strTablename) {
        return generateCoreClassName(strTablename) + MGR_SUFFIX;
    }
    
    protected String generateComparatorClassName() {
        return generateCoreClassName() + COMPARATOR_SUFFIX;
    }

    protected String generateComparatorClassName(String strTablename) {
        return generateCoreClassName(strTablename) + COMPARATOR_SUFFIX;
    }
            
    protected String generateIteratorClassName() {
        return generateCoreClassName() + ITERATOR_SUFFIX;
    }

    protected String generateIteratorClassName(String strTablename) {
        return generateCoreClassName(strTablename) + ITERATOR_SUFFIX;
    }

    protected String generateListenerClassName() {
        return generateCoreClassName() + LISTENER_SUFFIX;
    }

    protected String generateListenerClassName(String strTablename) {
        return generateCoreClassName(strTablename) + LISTENER_SUFFIX;
    }
}

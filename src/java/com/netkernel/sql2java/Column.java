//$Id: Column.java,v 1.1 2005-10-20 19:43:22 fbr Exp $

package com.netkernel.sql2java;

import java.sql.Types;

public class Column implements Cloneable {

    // List of possible mapped types.
    public static final int M_ARRAY = 0;
    public static final int M_BIGDECIMAL = 1;
    public static final int M_BOOLEAN = 2;
    public static final int M_BYTES = 3;
    public static final int M_CLOB = 4;
    public static final int M_SQLDATE =5 ;
    public static final int M_UTILDATE =6 ;
    public static final int M_DOUBLE = 7;
    public static final int M_FLOAT = 8;
    public static final int M_BLOB = 9;
    public static final int M_INTEGER = 10;
    public static final int M_LONG = 11;
    public static final int M_REF = 12;
    public static final int M_STRING =13 ;
    public static final int M_TIME = 14;
    public static final int M_TIMESTAMP = 15;
    public static final int M_URL = 16;
    public static final int M_OBJECT = 17;

    private String catalog, schema, tableName, name, remarks, defaultValue;
    private int size, decDigits, radix, nullable, ordinal;
    private short type;
    private boolean isPrimaryKey;
    private String strCheckingType = "";
    private String strForeignKeyColName= "";
    private String strForeignKeyTabName= "";
    private Database db;

    public String toString()
    {
    	return 	  "\n --------- " + tableName + "." + name + " --------- "
				+ "\n schema        = " + schema
				+ "\n tableName     = " + tableName
				+ "\n foreignCol    = " + strForeignKeyColName
				+ "\n foreignTab    = " + strForeignKeyTabName
				+ "\n catalog       = " + catalog
				+ "\n remarks       = " + remarks
				+ "\n defaultValue  = " + defaultValue
				+ "\n decDigits     = " + decDigits
				+ "\n radix         = " + radix
				+ "\n nullable      = " + nullable
				+ "\n ordinal       = " + ordinal
				+ "\n size          = " + size
				+ "\n type          = " + type + " "
				+ "\n isPrimaryKey  = " + (isPrimaryKey ? "true":"false");
    }

    public String getForeignKeyColName() { 
		return strForeignKeyColName;
    }

    public void setForeignKeyColName(String strValue) { 
        if (isReserved(strValue)) {
            strForeignKeyColName= "z"+strValue;
        } else {
            strForeignKeyColName= strValue;
        }
    }

    public String getForeignKeyTabName() { return strForeignKeyTabName;}

    public void setForeignKeyTabName(String strValue) { strForeignKeyTabName= strValue;}

    public Column getForeignColumn()
    {
        return db.getTable(getForeignKeyTabName()).getColumn(getForeignKeyColName());
    }

    // in case this column is pointing to another key, then we provide a way to get this
    // key.
    private Column pointsTo = null;

    public void setPointsTo(Column c) { pointsTo = c; }

    public Column getPointsTo() { return pointsTo; }

    public void setCheckingType(String strValue){this.strCheckingType = strValue;}

    public String getCheckingType(){return strCheckingType; }

    public void setDatabase(Database db) { this.db = db; }

    public void setCatalog(String catalog) { this.catalog = catalog; }

    public void setSchema(String schema) { this.schema = schema; }

    public void setTableName(String tableName) { this.tableName = tableName; }

    public void setName(String name) { 
        if (isReserved(name)) {
            this.name = "z"+name;
        } else {
            this.name = name;
        }
    }

    public void setType(short type) { this.type = type; }

    public void setSize(int size) { this.size = size; }

    public void setDecimalDigits(int decDigits) { this.decDigits = decDigits; }

    public void setRadix(int radix) { this.radix = radix; }

    public void setNullable(int nullable) { this.nullable = nullable; }

    public void setRemarks(String remarks) { this.remarks = remarks; }

    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public void setOrdinalPosition(int ordinal) { this.ordinal = ordinal; }

    public void isPrimaryKey(boolean isPrimaryKey) { this.isPrimaryKey = isPrimaryKey; }

    public String getCatalog() { return catalog; }

    public String getSchema() { return schema; }

    public String getTableName() { return tableName; }

    public String getName() { return name; }

    public short getType() { return type; }

    public int getSize() { return size; }

    public int getDecimalDigits() { return decDigits; }

    public int getRadix() { return radix; }

    public int getNullable() { return nullable; }

    public String getRemarks() { return remarks; }

    public String getDefaultValue() { return defaultValue; }

    public int getOrdinalPosition() { return ordinal; }

    public boolean isPrimaryKey() { return isPrimaryKey; }

    public String getFullName() { return tableName + "." + getName(); }

    public String getConstName() { return getName().toUpperCase(); }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    private void tuoe() {
        throw new UnsupportedOperationException("not supported yet: "+ getTableName() + "." + getName() + " " + getJavaTypeAsTypeName());
    }

    private void tiae() {
        throw new IllegalArgumentException("No primary type associated: " + getTableName() + "." + getName() );
    }

    public int getMappedType() {
        switch(getType()) {
        case Types.ARRAY: return M_ARRAY;
        case Types.BIGINT : return M_LONG;
        case Types.BINARY : return M_BYTES;
        case Types.BIT : return M_BOOLEAN;
        case Types.BLOB : return M_BLOB;
        case Types.BOOLEAN : return M_BOOLEAN;
        case Types.CHAR : return M_STRING;
        case Types.CLOB : return M_CLOB;
        case Types.DATALINK : return M_URL;
        case Types.DATE :
            if (CodeWriter.dateClassName.equals("java.util.Date")) return M_UTILDATE;
            if (CodeWriter.dateClassName.equals("java.sql.Date")) return M_SQLDATE;
            tuoe();
        case Types.DECIMAL : return getDecimalDigits() > 0 ? M_BIGDECIMAL : M_LONG;
        case Types.DISTINCT : return M_OBJECT;
        case Types.DOUBLE : return M_DOUBLE;
        case Types.FLOAT : return M_DOUBLE;
        case Types.INTEGER :return M_INTEGER;
        case Types.JAVA_OBJECT : return M_OBJECT;
        case Types.LONGVARBINARY :return M_BYTES;
        case Types.LONGVARCHAR : return M_STRING;

//        case Types.NULL : return M_NULL;

        case Types.NUMERIC : return getDecimalDigits() > 0 ? M_BIGDECIMAL : M_LONG;
        case Types.OTHER : return M_OBJECT;
        case Types.REAL : return M_FLOAT;
        case Types.REF : return M_REF;
        case Types.SMALLINT :return M_INTEGER;
        case Types.STRUCT : return M_OBJECT;
        case Types.TIME :
            if (CodeWriter.timeClassName.equals("java.util.Date")) return M_UTILDATE;
            if (CodeWriter.timeClassName.equals("java.sql.Time")) return M_TIME;
            tuoe();
        case Types.TIMESTAMP : 
            if (CodeWriter.timestampClassName.equals("java.util.Date")) return M_UTILDATE;
            if (CodeWriter.timestampClassName.equals("java.sql.Timestamp")) return M_TIMESTAMP;
            tuoe();
        case Types.TINYINT :return M_INTEGER;
        case Types.VARBINARY :return M_BYTES;
        case Types.VARCHAR :return M_STRING;
        default:tuoe();
        }
        return -1;
    }

    // Convernient helpers:

    public String getJavaType() 
    {        
        switch (getMappedType())
        {
        case M_ARRAY: return "Array";
        case M_BIGDECIMAL: return "java.math.BigDecimal";
        case M_BOOLEAN: return "Boolean";
        case M_BYTES: return "byte[]";
        case M_CLOB: return "Clob";
        case M_SQLDATE: return "java.sql.Date";
        case M_UTILDATE: return "java.util.Date";
        case M_DOUBLE: return "Double";
        case M_FLOAT: return "Float";
        case M_INTEGER: return "Integer";
        case M_LONG: return "Long";
        case M_REF: return "Ref";
        case M_STRING: return "String";
        case M_TIME: return "java.sql.Time";
        case M_TIMESTAMP: return "java.sql.Timestamp";
        case M_URL: return "java.net.URL";
        case M_OBJECT: return "Object";
        }
        return null;
    }

    public String getJavaPrimaryType() throws IllegalArgumentException
    {
        switch (getMappedType())
        {
        case M_BOOLEAN: return "boolean";
        case M_SQLDATE: return "long";
        case M_UTILDATE: return "long";
        case M_DOUBLE: return "double";
        case M_FLOAT: return "float";
        case M_INTEGER: return "int";
        case M_LONG: return "long";
        case M_TIME: return "long";
        case M_TIMESTAMP: return "long";
        default:tiae();
        }
        return null;
    }

    public String getJavaTypeAsTypeName()
    {
        switch (getType())
        {
        case Types.ARRAY:          return "Types.ARRAY";           
        case Types.BIGINT :        return "Types.BIGINT";         
        case Types.BINARY :        return "Types.BINARY";         
        case Types.BIT :           return "Types.BIT";            
        case Types.BLOB :          return "Types.BLOB";           
        case Types.BOOLEAN :       return "Types.BOOLEAN";        
        case Types.CHAR :          return "Types.CHAR";           
        case Types.CLOB :          return "Types.CLOB";           
        case Types.DATALINK :      return "Types.DATALINK";       
        case Types.DATE :          return "Types.DATE";           
        case Types.DECIMAL :       return "Types.DECIMAL";        
        case Types.DISTINCT :      return "Types.DISTINCT";       
        case Types.DOUBLE :        return "Types.DOUBLE";         
        case Types.FLOAT :         return "Types.FLOAT";          
        case Types.INTEGER :       return "Types.INTEGER";        
        case Types.JAVA_OBJECT :   return "Types.JAVA_OBJECT";    
        case Types.LONGVARBINARY : return "Types.LONGVARBINARY";  
        case Types.LONGVARCHAR :   return "Types.LONGVARCHAR";    
        case Types.NULL :          return "Types.NULL";           
        case Types.NUMERIC :       return "Types.NUMERIC";        
        case Types.OTHER :         return "Types.OTHER";          
        case Types.REAL :          return "Types.REAL";           
        case Types.REF :           return "Types.REF";            
        case Types.SMALLINT :      return "Types.SMALLINT";       
        case Types.STRUCT :        return "Types.STRUCT";         
        case Types.TIME :          return "Types.TIME";           
        case Types.TIMESTAMP :     return "Types.TIMESTAMP";      
        case Types.TINYINT :       return "Types.TINYINT";        
        case Types.VARBINARY :     return "Types.VARBINARY";      
        case Types.VARCHAR :       return "Types.VARCHAR";        
        default:tuoe();
        }
        return null;
    }

    public boolean isColumnNumeric()
    {
        switch (getMappedType())
        {
        case M_DOUBLE:  // fall through
        case M_FLOAT:   // fall through
        case M_INTEGER: // fall through
        case M_LONG: return true;
        default: return false;
        }
    }

    public boolean hasCompareTo() throws Exception
    {
        switch(getMappedType())
        {
        case M_ARRAY: return false;
        case M_BIGDECIMAL: return true;
        case M_BOOLEAN: return false; // USE EQUALS INSTEAD
        case M_BYTES: return false;
        case M_CLOB: return false;
        case M_SQLDATE: return true;
        case M_UTILDATE: return true;
        case M_DOUBLE: return true;
        case M_FLOAT: return true;
        case M_INTEGER: return true;
        case M_LONG: return true;
        case M_REF: return false;
        case M_STRING: return true;
        case M_TIME: return true;
        case M_TIMESTAMP: return true;
        case M_URL: return false;
        case M_OBJECT: return false;
        default: return false;
        }
    }

    public boolean useEqualsInSetter() throws Exception
    {
        switch(getMappedType())
        {
        case M_BOOLEAN: return true;
        default: return false;
        }
    }

    public String getResultSetMethodObject(String pos)
    {
        switch (getMappedType())
        {
        case M_ARRAY: return "rs.getArray(" + pos + ")";
        case M_LONG : return CodeWriter.MGR_CLASS + ".getLong(rs, "+pos+")";
        case M_BYTES : return "rs.getBytes(" + pos + ")";
        case M_BLOB : return "rs.getBlob("+pos+")";
        case M_BOOLEAN :  return CodeWriter.MGR_CLASS + ".getBoolean(rs, "+pos+")";
        case M_STRING : return "rs.getString(" + pos + ")";
        case M_CLOB : return "rs.getClob("+pos+")";
        case M_URL : return "rs.getURL("+pos+")";
        case M_BIGDECIMAL : return "rs.getBigDecimal(" + pos + ")";
        case M_DOUBLE : return CodeWriter.MGR_CLASS + ".getDouble(rs, "+pos+")";
        case M_FLOAT :  return CodeWriter.MGR_CLASS + ".getFloat(rs, "+pos+")";
        case M_INTEGER : return CodeWriter.MGR_CLASS + ".getInteger(rs, "+pos+")";

        case M_OBJECT : return "rs.getObject("+pos+")";
        case M_REF : return "rs.getRef("+pos+")";
        case M_SQLDATE : return "rs.getDate(" + pos + ")";
        case M_TIME :return "rs.getTime(" + pos + ")";
        case M_TIMESTAMP :return "rs.getTimestamp(" + pos + ")";
        case M_UTILDATE : 
            switch(getType()) {
            case Types.TIME:  return "rs.getTime(" + pos + ")";
            case Types.TIMESTAMP: return "rs.getTimestamp(" + pos + ")";
            case Types.DATE: return "rs.getDate(" + pos + ")";
            }                       
        default:tuoe();
        }
        return null;
    }


    /**
     * Maps the SQL type for the column to a "set" method to call on
     * the PreparedStatement.
     * <br>
     * This is used when generating Manager classes. There are some
     * variations in how different databases may deal with this, so you
     * might look at the OracleManagerWriter to see how you might have
     * to override this method for your particular database.
     *
     * @param var Java code that represents how we'll get access to the
     *            value to set. You should be able to use this value
     *            verbatim. Check out the source to this method to
     *            see what we mean.
     * @param pos The position to pass as the first argument of the set
     *            method. You will be able to use this verbatim too.
     * @return    The string to write into the generated Manager .java file.
     */
    public String getPreparedStatementMethod(String var,int pos)
    {
      return getPreparedStatementMethod(var, Integer.toString(pos));
    }

    public String getPreparedStatementMethod(String var, String pos)
    {
        switch(getMappedType())
        {
        case M_ARRAY: return "ps.setArray(" + pos + ", " + var + ");";
        case M_LONG: return "Manager.setLong(ps, "+pos+", "+var+");";
        case M_BYTES: return "ps.setBytes(" + pos + ", " + var + ");";
        case M_BLOB : return "ps.setBlob(" + pos + ", " + var + ");";
        case M_BOOLEAN : return "Manager.setBoolean(ps, "+pos+", "+var+");";
        case M_STRING:return "ps.setString(" + pos + ", " + var + ");";
        case M_CLOB : return "ps.setClob(" + pos + ", " + var + ");";
        case M_URL : return "ps.setURL(" + pos + ", " + var + ");";
        case M_BIGDECIMAL: return "ps.setBigDecimal(" + pos + ", "  + var + ");";
        case M_DOUBLE:  return "Manager.setDouble(ps, "+pos+", "+var+");";
        case M_INTEGER:  return "Manager.setInteger(ps, "+pos+", "+var+");";
        case M_OBJECT : return "ps.setObject(" + pos + ", " + var + ");";
        case M_FLOAT:  return "Manager.setFloat(ps, "+pos+", "+var+");";
        case M_SQLDATE: return "ps.setDate(" + pos + ", " + var + ");";
        case M_TIME: return "ps.setTime(" + pos + ", " + var + ");";
        case M_TIMESTAMP: return "ps.setTimestamp(" + pos + ", " + var + ");";
        case M_UTILDATE:
            switch(getType()) {
            case Types.TIMESTAMP: return "if (" +  var + " == null) ps.setNull("+pos+", "+getJavaTypeAsTypeName()+"); else ps.setTimestamp(" + pos + ", new java.sql.Timestamp(" + var + ".getTime()));";
            case Types.DATE: return "if (" +  var + " == null) ps.setNull("+pos+", "+getJavaTypeAsTypeName()+"); else ps.setDate(" + pos + ", new java.sql.Date(" + var + ".getTime()));";
            case Types.TIME: return "if (" +  var + " == null) ps.setNull("+pos+", "+getJavaTypeAsTypeName()+"); else ps.setTime(" + pos + ", new java.sql.Time(" + var + ".getTime()));";
            default: return null;
            }
        case M_REF: return "ps.setRef(" + pos + ", " + var + ");";
        default: return "ps.setObject(" + pos + ", " + var + ");";
        }
    }

    static boolean isReserved(String s) {
        for(int i=0; i<reserved_words.length; i++) {
            if (s.compareToIgnoreCase(reserved_words[i]) == 0) {
                return true;
            }
        }
        return false;
    }

    static String [] reserved_words = new String[] {
        "null",
        "true",
        "false",
        "abstract",
        "double", 
        "int", 
        "strictfp",
        "boolean", 
        "else", 
        "interface",
        "super", 
        "break", 
        "extends", 
        "long", 
        "switch", 
        "byte", 
        "final", 
        "native", 
        "synchronized",
        "case", 
        "finally", 
        "new",
        "this", 
        "catch", 
        "float",
        "package",
        "throw", 
        "char",
        "for",
        "private",
        "throws",
        "class",
        "goto",
        "protected",
        "transient",  
        "const",
        "if",
        "public",
        "try", 
        "continue",
        "implements", 
        "return", 
        "void", 
        "default", 
        "import", 
        "short", 
        "volatile", 
        "do", 
        "instanceof", 
        "static", 
        "while",
        "assert"
    };
}

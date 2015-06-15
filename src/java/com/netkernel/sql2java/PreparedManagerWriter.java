//$Id: PreparedManagerWriter.java,v 1.7 2009-05-27 20:36:46 fbr Exp $

package com.netkernel.sql2java;

import java.util.*;

public class PreparedManagerWriter extends CodeWriter
{
	protected static final String POST_INSERT_BLOCK = "postinsert";
	protected static final String PRE_INSERT_BLOCK 	= "preinsert";
	protected static final String POST_UPDATE_BLOCK = "postupdate";
	protected static final String PRE_UPDATE_BLOCK 	= "preupdate";
  
    public boolean isStringInArrayList(List<String> pArrayList, String strValue)
    {
        for(int iIndex = 0; iIndex < pArrayList.size(); iIndex ++)
        {
            String strP = pArrayList.get(iIndex);
            if (strP.equalsIgnoreCase(strValue))
                return true;
        }
        return false;
    }

    /**
     * A fairly monolithic method that writes out the Manager class for
     * a specific table.
     * <br>
     * The PreparedManagerWriter is a bit more sophisticated than the
     * DynamicManagerWriter because it provides a larger number of load
     * methods (one per exported foreign key) and the ability to pass in
     * a Connection pObject to methods (which enables transactions to
     * span multiple method invocations).
     */
    protected void writeManager() throws Exception
    {
        // Check if primary key exists for this table
        // If no primary key, then we can't manage it..

        // Resolve class and package
        className = generateManagerClassName();
        String beanClass = generateBeanClassName();
        pkg = basePackage; // + "." + convertName(table.getName()).toLowerCase();

        // import keys
        Column pk[] = table.getPrimaryKeys();
        Column cols[] = table.getColumns();
        // Setup the PrintWriter and read in existing Java source file
        initWriter();

        // Start class
        indent(0, "package " + pkg + ";");
        writer.println();
        indent(0, "import java.sql.*;");
        indent(0, "import java.util.*;");
        indent(0, "import java.util.concurrent.*;");
        indent(0, "import org.slf4j.*;");
        indent(0, userCode.getBlock(IMPORT_BLOCK));

        writer.println();
        indent(0, "/**");
        indent(0, " * Handles database calls for the " + table.getName() + " table.");
        if (table.getRemarks() != null && table.getRemarks().length()>0)
            indent(1, " * Remarks: " + table.getRemarks());
        indent(0, " */");
        indent(0, "public class " + className);
        indent(0, userCode.getBlock(EXTENDS_BLOCK));
        indent(0, "{");
        indent(0, "");

        writeInitializers(cols);

        // name of the table
        //indent(1, "private static final String TABLE_NAME = \"" + table.getName() + "\";");
        indent(0, "");

        writeTableFields(cols);

//         // write out static array of int -> column name mappings
//         indent(1, "/** create an array of string containing all the fields of the " + table.getName() + " table. */");
//         indent(1, "private static final String[] TABLEFIELD_NAMES = ");
//         indent(1, "{");
//         for(int i = 0; i < cols.length; i++)
//         {
//             indent(1, "   " + (i==0 ? "\"" : ",\"") + cols[i].getName() + "\"");
//         }
//         indent(1, "};");
//         indent(1, "");

        // write out all column names as comma separated string
        StringBuffer allFields = new StringBuffer();
        for(int i = 0; i < cols.length; i++)
        {
            if(i != 0)
            {
                allFields.append(CodeWriter.LINE_SEP);
                allFields.append("                            ");
                allFields.append("+ \",");
            }
            else
                allFields.append("\"");
                allFields.append(cols[i].getFullName());
                allFields.append("\"");
        }
        allFields.append(";");

        indent(1, "/**");
        indent(1, " * Field that contains the comma separated fields of the " + table.getName() + " table.");
        indent(1, " */");
        indent(1, "private static final String ALL_FIELDS = " + allFields);
        indent(0, "");
        indent(1, "private static " + className +" singleton = new "+className+"();");
        indent(1, "private Logger logger = LoggerFactory.getLogger(getClass());");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Get the " + generateManagerClassName(table.getName()) + " singleton.");
        indent(1, " *");
        indent(1, " * @return " + generateManagerClassName(table.getName()) + " ");
        indent(1, " */");
        indent(1, "public static " + generateManagerClassName(table.getName()) + " getInstance()");
        indent(1, "{");
        indent(1, "    return singleton;");
        indent(1, "}");
        indent(0, "");
        
        indent(1, "/**");
        indent(1, " * Sets your own " + generateManagerClassName(table.getName()) + " instance.");
        indent(1, " <br>");
        indent(1, " * This is optional, by default we provide it for you.");
        indent(1, " */");
        indent(1, "synchronized public static void setInstance("+ generateManagerClassName(table.getName())+" instance)");
        indent(1, "{");
        indent(1, "    singleton = instance;");
        indent(1, "}");
        indent(0, "");

        indent(0, "");
        indent(1, "/**");
        indent(1, " * Creates a new " + beanClass + " instance.");
        indent(1, " *");
        indent(1, " * @return the new " + beanClass + " ");
        indent(1, " */");
        indent(1, "public " + beanClass + " create"+beanClass+"()");
        indent(1, "{");
        indent(1, "    return new "+beanClass+"();");
        indent(1, "}");
        indent(0, "");

        // setup primary key strings
        StringBuffer keys = new StringBuffer();
        for(int i = 0; i < pk.length; i++)
        {
            if(i != 0)
            {
                keys.append(", ");
            }

            keys.append(pk[i].getJavaType());
            keys.append(" ");
            keys.append(getVarName(pk[i]));
        }

        
        writePrimaryKeyMethods();
        writeForeignKeyMethods();
        writeForeignKeyGetterAndSetter();
        writeAllMethods();
        writeSaveMethods();
        writeLoadUsingTemplateMethods();
        writeDeleteUsingTemplateMethods();


        // look for N/N linked tables
        Table[] pRelationTables = db.getRelationTable(table);

        for (int iIndex = 0; iIndex < pRelationTables.length; iIndex ++)
        {
            Table[] pLinkedTables = pRelationTables[iIndex].linkedTables(db, table);
            for (int iLinkedIndex = 0; iLinkedIndex < pLinkedTables.length; iLinkedIndex ++)
            {
                if (iLinkedIndex == 0 && iIndex == 0) {
                    indent(1, "");
                    indent(1, "");
                    indent(1, "///////////////////////////////////////////////////////////////////////");
                    indent(1, "// MANY TO MANY: LOAD OTHER BEAN VIA JUNCTION TABLE ");
                    indent(1, "///////////////////////////////////////////////////////////////////////");
                }

                Table pRelationTable = pRelationTables[iIndex];
                Table pLinkedTable = pLinkedTables[iLinkedIndex];

                String strLinkedCore = generateCoreClassName(pLinkedTable.getName());
                String strLinkedBean = generateBeanClassName(pLinkedTable.getName());

                String strRelationCore = generateCoreClassName(pRelationTable.getName());

                Column pLocalKey = pRelationTable.getForeignKeyFor(table);
                Column pExternalKey = pRelationTable.getForeignKeyFor(pLinkedTable);

                indent(1, "/**");
                indent(1, " * Retrieves an array of " + strLinkedBean + " using the relation table " + 
                       strRelationCore + " given a " + beanClass + " object.");
                indent(1, " *");
                indent(1, " * @param pObject the " + beanClass + " pObject to be used");
                indent(1, " * @return an array of " + strLinkedBean + " ");
                indent(1, " */");
                indent(1, "// MANY TO MANY");
                indent(1, "public " + strLinkedBean + "[] load" + strLinkedCore + "Via" + strRelationCore + "(" + beanClass + " pObject) throws SQLException");
                indent(1, "{");
                indent(1, "     Connection c = null;");
                indent(1, "     PreparedStatement ps = null;");
                indent(1, "     String strSQL =      \" SELECT \"");
                indent(1, "                     + \"        *\"");
                indent(1, "                     + \" FROM  \"");
                indent(1, "                     + \"        " + pLinkedTable.getName() + "," + pRelationTable.getName() + "\"");
                indent(1, "                     + \" WHERE \"    ");
                indent(1, "                     + \"     " + pLocalKey.getForeignKeyTabName() + "." + pLocalKey.getForeignKeyColName() + " = ?\"");
                indent(1, "                     + \" AND " + pExternalKey.getForeignKeyTabName() + "." + pExternalKey.getForeignKeyColName() + " = " + pExternalKey.getTableName() + "." + pExternalKey.getName() +"\";");

                indent(1, "     try");
                indent(1, "     {");
                indent(1, "         c = getConnection();");
                indent(1, "         ps = c.prepareStatement(strSQL,ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
                indent(1, "         " + pLocalKey.getPreparedStatementMethod("pObject." + getGetMethod(pLocalKey) + "()", 1));
                indent(1, "         return " + generateManagerClassName(pLinkedTable.getName()) + ".getInstance().loadByPreparedStatement(ps);");
                indent(1, "     }");
                indent(1, "     finally");
                indent(1, "     {");
                indent(1, "        getManager().close(ps);");
                indent(1, "        freeConnection(c);");
                indent(1, "     }");
                indent(1, "}");
                indent(0, "");
            }
        }

        indent(0, "");
        indent(0, "");
        indent(1, "///////////////////////////////////////////////////////////////////////");
        indent(1, "// COUNT ");
        indent(1, "///////////////////////////////////////////////////////////////////////");

        // count
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Retrieves the number of rows of the table " + table.getName() + ".");
        indent(1, " *");
        indent(1, " * @return the number of rows returned");
        indent(1, " */");
        indent(1, "//78");
        indent(1, "public int countAll() throws SQLException");
        indent(1, "{");
        indent(1, "    return countWhere(\"\");");
        indent(1, "}");
        indent(0, "");
        indent(0, "");

        // countWhere
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Retrieves the number of rows of the table " + table.getName() + " with a 'where' clause.");
		indent(1, " * It is up to you to pass the 'WHERE' in your where clausis.");
        indent(1, " *");
        indent(1, " * @param where the restriction clause");
        indent(1, " * @return the number of rows returned");
        indent(1, " */");
        indent(1, "public int countWhere(String where) throws SQLException");
        indent(1, "{");
        indent(1, "    String sql = \"select count(*) as MCOUNT from " + table.getName() + " \" + where;");
        indent(1, "    Connection c = null;");
        indent(1, "    Statement pStatement = null;");
        indent(1, "    ResultSet rs =  null;");
        indent(1, "    try ");
        indent(1, "    {");
        indent(1, "        int iReturn = -1;    ");
        indent(1, "        c = getConnection();");
        indent(1, "        pStatement = c.createStatement();");
        indent(1, "        rs =  pStatement.executeQuery(sql);");
        indent(1, "        if (rs.next())");
        indent(1, "        {");
        indent(1, "            iReturn = rs.getInt(\"MCOUNT\");");
        indent(1, "        }");
        indent(1, "        if (iReturn != -1)");
        indent(1, "            return iReturn;");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(pStatement, rs);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "   throw new SQLException(\"Error in countWhere\");");
        indent(1, "}");
        indent(0, "");

        // countByPreparedStatement
        indent(1, "/**");
        indent(1, " * Retrieves the number of rows of the table " + table.getName() + " with a prepared statement.");
        indent(1, " *");
        indent(1, " * @param ps the PreparedStatement to be used");
        indent(1, " * @return the number of rows returned");
        indent(1, " */");
        indent(1, "//82");
        indent(1, "int countByPreparedStatement(PreparedStatement ps) throws SQLException");
        indent(1, "{");
        indent(1, "    ResultSet rs =  null;");
        indent(1, "    try ");
        indent(1, "    {");
        indent(1, "        int iReturn = -1;");
        indent(1, "        rs = ps.executeQuery();");
        indent(1, "        if (rs.next())");
        indent(1, "            iReturn = rs.getInt(\"MCOUNT\");");
        indent(1, "        if (iReturn != -1)");
        indent(1, "            return iReturn;");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(rs);");
        indent(1, "    }");
        indent(1, "   throw new SQLException(\"Error in countByPreparedStatement\");");
        indent(1, "}");
        indent(0, "");

        // count
        indent(1, "/**");
        indent(1, " * Looks for the number of elements of a specific " + beanClass + " pObject given a c");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to look for");
        indent(1, " * @return the number of rows returned");
        indent(1, " */");
        indent(1, "//83");
        indent(1, "public int countUsingTemplate(" + beanClass + " pObject) throws SQLException");
        indent(1, "{");
//        indent(1, "    StringBuffer where = new StringBuffer(\"\");");
        indent(1, "    Connection c = null;");
        indent(1, "    PreparedStatement ps = null;");
        indent(1, "    StringBuffer _sql = null;");
        indent(1, "    StringBuffer _sqlWhere = null;");
		indent(1, "");
        indent(1, "    try");
        indent(1, "    {");
        indent(1, "            _sql = new StringBuffer(\"SELECT count(*) as MCOUNT  from " + table.getName() + " WHERE \");");
        indent(1, "            _sqlWhere = new StringBuffer(\"\");");
        indent(1, "            int _dirtyCount = 0;");
        for(int i = 0; i < cols.length; i++)
        {
			  indent(1, "");
              indent(1, "            if (pObject." + getModifiedMethod(cols[i]) + "()) {");
              indent(1, "                _dirtyCount++; ");
              indent(1, "                _sqlWhere.append((_sqlWhere.length() == 0) ? \" \" : \" AND \").append(\"" + cols[i].getName() + "= ?\");");
              indent(1, "            }");
        }
		indent(1, "");
        indent(1, "            if (_dirtyCount == 0)");
        indent(1, "               throw new SQLException (\"The pObject to look is unvalid : not initialized !\");");
		indent(1, "");
        indent(1, "            _sql.append(_sqlWhere);");
        indent(1, "            c = getConnection();");        
        indent(1, "            ps = c.prepareStatement(_sql.toString(),ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
		indent(1, "");
        indent(1, "            _dirtyCount = 0;");
        for(int i = 0; i < cols.length; i++)
        {
			  indent(1, "");
              indent(1, "            if (pObject." + getModifiedMethod(cols[i]) + "()) {");
              indent(1, "                " + cols[i].getPreparedStatementMethod("pObject."+getGetMethod(cols[i])+"()","++_dirtyCount"));
			  indent(1, "            }");
        }
        indent(1, "");
        indent(1, "            return countByPreparedStatement(ps);");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(ps);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");

        indent(0, "");
        indent(0, "");
        indent(1, "///////////////////////////////////////////////////////////////////////");
        indent(1, "// DECODE RESULT SET ");
        indent(1, "///////////////////////////////////////////////////////////////////////");

        // write decodeRow()
        indent(1, "/**");
        indent(1, " * Transforms a ResultSet iterating on the " + table.getName() + " on a " + beanClass + " pObject.");
        indent(1, " *");
        indent(1, " * @param rs the ResultSet to be transformed");
        indent(1, " * @return pObject resulting " + beanClass + " pObject");
        indent(1, " */");
        indent(1, "//72");
        indent(1, "public " + beanClass + " decodeRow(ResultSet rs) throws SQLException");
        indent(1, "{");
        indent(1, "    " + beanClass + " pObject = create" + beanClass + "();");
        for(int i = 0; i < cols.length; i++)
        {
                indent(1, "    pObject." + getSetMethod(cols[i]) + "(" + cols[i].getResultSetMethodObject(Integer.toString(i+1)) + ");");
        }
        indent(0, "");
        indent(1, "    pObject.isNew(false);");
        indent(1, "    pObject.resetIsModified();");
        indent(0, "");
        indent(1, "    return pObject;");
        indent(1, "}");
        indent(0, "");

        // write decodeRow() that accepts a field list
        indent(1, "/**");
        indent(1, " * Transforms a ResultSet iterating on the " + table.getName() + " table on a " + beanClass + " pObject according to a list of fields.");
        indent(1, " *");
        indent(1, " * @param rs the ResultSet to be transformed");
        indent(1, " * @param fieldList table of the field's associated constants");
        indent(1, " * @return pObject resulting " + beanClass + " pObject");
        indent(1, " */");
        indent(1, "//73");
        indent(1, "public " + beanClass + " decodeRow(ResultSet rs, int[] fieldList) throws SQLException");
        indent(1, "{");
        indent(1, "    " + beanClass + " pObject = create" + beanClass + "();");
        indent(1, "    int pos = 0;");
        indent(1, "    for(int i = 0; i < fieldList.length; i++)");
        indent(1, "    {");
        indent(1, "        switch(fieldList[i]) {");
        
        for(int i = 0; i < cols.length; i++)
        {
            indent(1, "            case ID_" + cols[i].getConstName() + ":");
            indent(1, "                ++pos;");
            indent(4,   "    pObject." +getSetMethod(cols[i]) + "(" + cols[i].getResultSetMethodObject("pos") + ");");
            indent(4,     "    break;");
        }
        indent(1, "        }");
        indent(1, "    }");
        indent(1, "    pObject.isNew(false);");
        indent(1, "    pObject.resetIsModified();");
        indent(0, "");
        indent(1, "    return pObject;");
        indent(1, "}");
        indent(0, "");

        indent(1, "//////////////////////////////////////");
        indent(1, "// PREPARED STATEMENT LOADER");
        indent(1, "//////////////////////////////////////");
        indent(0, "");

        // loadByPreparedStatement
        indent(1, "/**");
        indent(1, " * Loads all the elements using a prepared statement.");
        indent(1, " *");
        indent(1, " * @param ps the PreparedStatement to be used");
        indent(1, " * @return an array of " + beanClass + " ");
        indent(1, " */");
        indent(1, "//41");
        indent(1, "public " + beanClass + "[] loadByPreparedStatement(PreparedStatement ps) throws SQLException");
        indent(1, "{");
        indent(1, "    return loadByPreparedStatement(ps, null);");
        indent(1, "}");
        indent(0, "");

        // loadByPreparedStatement with fieldList
        indent(1, "/**");
        indent(1, " * Loads all the elements using a prepared statement specifying a list of fields to be retrieved.");
        indent(1, " *");
        indent(1, " * @param ps the PreparedStatement to be used");
        indent(1, " * @param fieldList table of the field's associated constants");
        indent(1, " * @return an array of " + beanClass + " ");
        indent(1, " */");
        indent(1, "public " + beanClass + "[] loadByPreparedStatement(PreparedStatement ps, int[] fieldList) throws SQLException");
        indent(1, "{");
        indent(1, "    ResultSet rs =  null;");
        indent(1, "    java.util.ArrayList<"+beanClass+"> v =  null;");
        indent(1, "    try");
        indent(1, "    {");
        indent(1, "        rs =  ps.executeQuery();");
        indent(1, "        v = new java.util.ArrayList<"+beanClass+">();");
        indent(1, "        while(rs.next())");
        indent(1, "        {");
        indent(1, "            if(fieldList == null)");
        indent(1, "                v.add(decodeRow(rs));");
        indent(1, "            else ");
        indent(1, "                v.add(decodeRow(rs, fieldList));");
        indent(1, "        }");
        indent(1, "        return v.toArray(new " + beanClass + "[0]);");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        if (v != null) { v.clear(); v = null;}");
        indent(1, "        getManager().close(rs);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");

        indent(1, "///////////////////////////////////////////////////////////////////////");
        indent(1, "// LISTENER ");
        indent(1, "///////////////////////////////////////////////////////////////////////");

        // listener
        indent(1, "private List<"+generateListenerClassName()+"> listeners = new ArrayList<"+generateListenerClassName()+">(5);");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Registers a unique " + generateListenerClassName() + " listener.");
        indent(1, " */");
        indent(1, "//66.5");
        indent(1, "public synchronized void addListener("+generateListenerClassName() +" listener) {");
        indent(1, "    if (listener != null) this.listeners.add(listener);");
        indent(1, "    else logger.error(\"Listener is null\", new IllegalStateException());");
        indent(1, "}");
        indent(0, "");

        // Remove listener
        indent(1, "/**");
        indent(1, " * Removes a unique " + generateListenerClassName() + " listener.");
        indent(1, " */");
        indent(1, "//66.6");
        indent(1, "public void removeListener("+generateListenerClassName() +" listener) {");
        indent(1, "    this.listeners.remove(listener);");
        indent(1, "}");
        indent(0, "");
        
        // beforeInsert
        indent(1, "/**");
        indent(1, " * Before the save of the " + beanClass + " pObject.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to be saved");
        indent(1, " */");
        indent(1, "//67");
        indent(1, "void beforeInsert(" + beanClass + " pObject) throws SQLException {");
        indent(1, "    for(Iterator<"+generateListenerClassName()+"> i = listeners.iterator(); i.hasNext();) {");
        indent(1, "        "+generateListenerClassName()+" listener = i.next();");
        indent(1, "        listener.beforeInsert(pObject);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");

        // afterInsert
        indent(1, "/**");
        indent(1, " * After the save of the " + beanClass + " pObject.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to be saved");
        indent(1, " */");
        indent(1, "//68");
        indent(1, "void afterInsert(" + beanClass + " pObject) throws SQLException {");
        indent(1, "    for(Iterator<"+generateListenerClassName()+"> i = listeners.iterator(); i.hasNext();) {");
        indent(1, "        "+generateListenerClassName()+" listener = i.next();");
        indent(1, "        listener.afterInsert(pObject);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");

        // beforeUpdate
        indent(1, "/**");
        indent(1, " * Before the update of the " + beanClass + " pObject.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to be updated");
        indent(1, " */");
        indent(1, "//69");
        indent(1, "void beforeUpdate(" + beanClass + " pObject) throws SQLException {");
        indent(1, "    for(Iterator<"+generateListenerClassName()+"> i = listeners.iterator(); i.hasNext();) {");
        indent(1, "        "+generateListenerClassName()+" listener = i.next();");
        indent(1, "        listener.beforeUpdate(pObject);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");

        // afterUpdate
        indent(1, "/**");
        indent(1, " * After the update of the " + beanClass + " pObject.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to be updated");
        indent(1, " */");
        indent(1, "//70");
        indent(1, "void afterUpdate(" + beanClass + " pObject) throws SQLException {");
        indent(1, "    for(Iterator<"+generateListenerClassName()+"> i = listeners.iterator(); i.hasNext();) {");
        indent(1, "        "+generateListenerClassName()+" listener = i.next();");
        indent(1, "        listener.afterUpdate(pObject);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        
        //beforeDelete
        indent(1, "/**");
        indent(1, " * Before the deletion of one or more " + beanClass);
        indent(1, " *");
        indent(1, " * @param the " + beanClass + "(s) to be deleted");
        indent(1, " */");
        indent(1, "//71");
        indent(1, "void beforeDelete("+beanClass+"[] beans) throws SQLException {");
        indent(1, "    for(Iterator<"+generateListenerClassName()+"> i = listeners.iterator(); i.hasNext();) {");
        indent(1, "        "+generateListenerClassName()+" listener = i.next();");
        indent(1, "        listener.beforeDelete(beans);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        // afterDelete
        indent(1, "/**");
        indent(1, " * After the deletion of one or more " + beanClass);
        indent(1, " *");
        indent(1, " * @param count the number of deleted " + beanClass + " object(s)");
        indent(1, " */");
        indent(1, "//72");
        indent(1, "void afterDelete("+beanClass+"[] deletedBeans) throws SQLException {");
        indent(1, "    for(Iterator<"+generateListenerClassName()+"> i = listeners.iterator(); i.hasNext();) {");
        indent(1, "        "+generateListenerClassName()+" listener = i.next();");
        indent(1, "        listener.afterDelete(deletedBeans);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        indent(1, "///////////////////////////////////////////////////////////////////////");
        indent(1, "// UTILS  ");
        indent(1, "///////////////////////////////////////////////////////////////////////");

        indent(0, "");
        indent(1, "/**");
        indent(1, " * Retrieves the manager object used to get connections.");
        indent(1, " *");
        indent(1, " * @return the manager used");
        indent(1, " */");
        indent(1, "//2");
        indent(1, "Manager getManager() {");
        indent(1, "    return Manager.getInstance();");
        indent(1, "}");
        indent(0, "");

        indent(1, "/**");   
        indent(1, " * Frees the connection.");   
        indent(1, " *");
        indent(1, " * @param c the connection to release");   
        indent(1, " */");   
        indent(1, "void freeConnection(Connection c) {");   
        indent(1, "    getManager().releaseConnection(c); // back to pool");   
        indent(1, "}"); 

        indent(1, "/**");
        indent(1, " * Gets the connection.");
        indent(1, " */");
        indent(1, "Connection getConnection() throws SQLException {");
        indent(1, "    return getManager().getConnection();");
        indent(1, "}");
        indent(0, "");

        if (table.getPrimaryKeys().length > 0) {
        	writeCacheControl();
        	writeCacheClass();
        }

        // End class
        writeEnd();
    }

	/**
	 * @param cols
	 */
	private void writeTableFields(Column[] cols) {
		// write out static array of int -> column name mappings
        indent(1, "/**");
		indent(1, " * Create an array of type string containing all the fields of the " + table.getName() + " table.");
		indent(1, " */");
		indent(1, "private static final String[] FIELD_NAMES = ");
		indent(1, "{");
		for(int i = 0; i < cols.length; i++) 
        {
            indent(1, "    " + (i==0 ? "\"" : ",\"") + cols[i].getFullName() + "\"");
        }
		indent(1, "};");
		indent(0, "");
	}

	/**
	 * @param cols
	 */
	private void writeInitializers(Column[] cols) {
		// Write the initializer block
        // write out all columns as a separate static int
        for(int i = 0; i < cols.length; i++)
        {
            indent(1, "/**");
            indent(1, " * Column "+  cols[i].getName() + " of type " + 
                   cols[i].getJavaTypeAsTypeName()+ " mapped to " + cols[i].getJavaType() + ".");
            indent(1, " */");
            indent(1, "public static final int ID_" + cols[i].getConstName() + 
                   " = " + i + ";");
            indent(1, "public static final int TYPE_" + cols[i].getConstName() + 
                   " = " + 
                   cols[i].getJavaTypeAsTypeName() + ";");
            indent(1, "public static final String NAME_" + cols[i].getConstName() + 
                   " = \"" + 
                   cols[i].getName() + "\";");
            indent(0, "");
        }
		indent(0, "");
	}
    
    public void writeSave(/*PrintWriter writer, Table table, Database db*/)
    {
        String beanClass = generateBeanClassName();
        
        // import keys
        Column pk[] = table.getPrimaryKeys();
        Column cols[] = table.getColumns();

        // write out all column names as comma separated string
        StringBuffer allFields = new StringBuffer();
        for(int i = 0; i < cols.length; i++)
        {
            if(i != 0)
            {
                allFields.append(CodeWriter.LINE_SEP);
                allFields.append("                            ");
                allFields.append("+ \",");
            }
            else
                allFields.append("\"");

            allFields.append(cols[i].getFullName());
            allFields.append("\"");
        }
        allFields.append(";");

        StringBuffer sql = new StringBuffer();

        // save
        indent(1, "/**");
        indent(1, " * Saves the " + beanClass + " pObject into the database.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to be saved");
        indent(1, " */");
        indent(1, "//100");
        indent(1, "public " + beanClass + " save(" + beanClass + " pObject) throws SQLException");
        indent(1, "{");
        indent(1, "    Connection c = null;");
        indent(1, "    PreparedStatement ps = null;");
        indent(1, "    StringBuffer _sql = null;");
		indent(0, "");
        indent(1, "    try");
        indent(1, "    {");
        indent(1, "        c = getConnection();");
        indent(1, "        if (pObject.isNew())");
        indent(1, "        { // SAVE ");
        //-------------------------------------
        writePreInsert(table);
        //-------------------------------------
        indent(1, "            beforeInsert(pObject); // listener callback");
        indent(1, "            int _dirtyCount = 0;");
        indent(1, "            _sql = new StringBuffer(\"INSERT into " + table.getName() + " (\");");
		indent(1, ""); 
        for(int i = 0; i < cols.length; i++)
        {
	        indent(1, "            if (pObject." + getModifiedMethod(cols[i]) + "()) {");
			indent(1, "                if (_dirtyCount>0) {");
			indent(1, "                    _sql.append(\",\");");
			indent(1, "                }");
       		indent(1, "                _sql.append(\"" + cols[i].getName() + "\");");
          	indent(1, "                _dirtyCount++;");
          	indent(1, "            }");
          	indent(0, "");
        }
        indent(1, "            _sql.append(\") values (\");");
		indent(1, "            if(_dirtyCount > 0) {");
		indent(1, "                _sql.append(\"?\");");
        indent(1, "                for(int i = 1; i < _dirtyCount; i++) {");
        indent(1, "                    _sql.append(\",?\");");
		indent(1, "                }");
		indent(1, "            }");
        indent(1, "            _sql.append(\")\");");
        indent(0, "");
        indent(1, "            ps = c.prepareStatement(_sql.toString(), "+preparedStatementArgsAsString()+");");
        indent(1, "            _dirtyCount = 0;");
		indent(0, "");
        for(int i = 0; i < cols.length; i++)
        {
            indent(1, "            if (pObject." + getModifiedMethod(cols[i]) + "()) {");
            indent(1, "                " + cols[i].getPreparedStatementMethod(
                       "pObject."+getGetMethod(cols[i])+"()",
                       "++_dirtyCount"));
			indent(1, "            }");  
			indent(1, "");  
        }
        indent(1, "            ps.executeUpdate();");
        //-------------------------------------
        writePostInsert(table);
        //-------------------------------------
		indent(1, "");
        indent(1, "            pObject.isNew(false);");
        indent(1, "            pObject.resetIsModified();");
        indent(1, "            afterInsert(pObject); // listener callback");
        indent(1, "        }");
        indent(1, "        else ");
        indent(1, "        { // UPDATE ");
        // ======= UPDATE ====================================================================================       
        if (pk.length == 0)
        {
            System.out.println("!! WARNING !! " + table.getName() + " does not have any primary key...");
        }
        indent(1, "            beforeUpdate(pObject); // listener callback");
        indent(1, "            _sql = new StringBuffer(\"UPDATE " + table.getName() + " SET \");");
		indent(1, "            boolean useComma=false;");
        for(int i = 0; i < cols.length; i++)
        {
            indent(0, "");
            indent(1, "            if (pObject." + getModifiedMethod(cols[i]) + "()) {");
			indent(1, "                if (useComma) {");
			indent(1, "                    _sql.append(\",\");");
			indent(1, "                } else {");
			indent(1, "                    useComma=true;");
			indent(1, "                }");			
	        indent(1, "                _sql.append(\"" + cols[i].getName() + "\").append(\"=?\");");
			indent(1, "            }");
        }

        if (pk.length > 0)
            indent(1, "            _sql.append(\" WHERE \");");
        sql.setLength(0);
        for(int i = 0; i < pk.length; i++)
        {
            if(i > 0)
                sql.append(" AND ");
            sql.append(pk[i].getFullName());
            sql.append("=?");
        }
        indent(1, "            _sql.append(\"" + sql + "\");");
        indent(1, "            ps = c.prepareStatement(_sql.toString(),ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
        indent(1, "            int _dirtyCount = 0;");
        for(int i = 0; i < cols.length; i++)
        {
            indent(0, "");
            indent(1, "            if (pObject." + getModifiedMethod(cols[i]) + "()) {");
            //indent(1, "               if (pObject."+getGetMethod(cols[i])+"() == null)");
            //indent(1, "                  ps.setNull(++_dirtyCount, "+cols[i].getJavaTypeAsTypeName()+");");
            //indent(1, "               else");
            indent(1, "                  " + cols[i].getPreparedStatementMethod(
                       "pObject."+getGetMethod(cols[i])+"()",
                       "++_dirtyCount"));
            indent(1, "            }");
        }
		indent(1, "");
        indent(1, "            if (_dirtyCount == 0) {");
        indent(1, "                 return pObject;");
		indent(1, "            }");
		indent(1, "");
        for(int i = 0; i < pk.length; i++)
        {
            indent(1, "            " + pk[i].getPreparedStatementMethod("pObject." + getGetMethod(pk[i]) + "()", "++_dirtyCount"));
        }
        indent(1, "            ps.executeUpdate();");
        indent(1, "            afterUpdate(pObject); // listener callback");
        indent(1, "            pObject.resetIsModified();");
        indent(1, "        }");
		indent(1, "");
		indent(1, "        return pObject;");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(ps);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
    }

    /**
     * Arguments passed to the prepared statement performing insert.
     * <br>
     * Can be overriden to retrieve auto generated keys.
     */
    protected String preparedStatementArgsAsString () {
        if ("auto".equalsIgnoreCase(Main.getProperty("generatedkey.retrieve", "")))
        {
            return "Statement.RETURN_GENERATED_KEYS";
        } 
        return "ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY";
    }

    /**
     * An empty method that you can override to generate code to be
     * inserted after the save() method inserts a row.
     * <br>
     * Typically this is useful for grabbing the auto-generated primary
     * key and setting the pObject's corresponding property with that
     * value. See the OracleManagerWriter for a sample implementation
     * that maps to a convention I frequently use.
     */
    protected void writePostInsert(Table table)
    {
        String mode = Main.getProperty("generatedkey.retrieve", "");

        if ("after".equalsIgnoreCase(mode)) 
        {
            String hint = getHint(table);
            Column pk[] = table.getPrimaryKeys();
            
            if (pk.length==1 && pk[0].isColumnNumeric())
            {
                Column pkc = pk[0];
				indent(4, "");
                indent(4, "if (!pObject." + getModifiedMethod(pkc) + "())");
                indent(4, "{");
                indent(5, "PreparedStatement ps2 = null;");
                indent(5, "ResultSet rs = null;");
                
                indent(5, "try { "); 
                indent(5, "    ps2 = c.prepareStatement(\""+ hint +"\");");
                indent(5, "    rs = ps2.executeQuery();");
                indent(5, "    if(rs.next()) {");
                indent(5, "        pObject." + getSetMethod(pkc) + "(" + pkc.getResultSetMethodObject("1")+ ");");
                indent(5, "    } else {");
                indent(5, "        getManager().log(\"ATTENTION: Could not retrieve generated key!\");");
                indent(5, "    }");
                indent(5, "} finally { ");            
                indent(5, "    getManager().close(ps2, rs);");
                indent(5, "}");
                indent(4, "}");
            }
        } 
        else if ("auto".equalsIgnoreCase(mode)) 
        {
            Column pk[] = table.getPrimaryKeys();
            
            if (pk.length==1 && pk[0].isColumnNumeric())
            {
                Column pkc = pk[0];
                
                indent(4, "if (!pObject." + getModifiedMethod(pkc) + "())");
                indent(4, "{");
                indent(5, "ResultSet rs = ps.getGeneratedKeys();");
                indent(5, "try { "); 
                indent(5, "    if(rs.next())");
                indent(5, "        pObject." + getSetMethod(pkc) + "(" + pkc.getResultSetMethodObject("1")+ ");");
                indent(5, "    else");
                indent(5, "        getManager().log(\"ATTENTION: Could not retrieve auto generated key!\");");
                indent(5, "} finally { ");            
                indent(5, "    getManager().close(rs);");
                indent(5, "}");
                indent(4, "}");
            }
        }
    }

    protected void writePreInsert(Table table)
    {
        String before = Main.getProperty("generatedkey.retrieve", "");

        if (!"before".equalsIgnoreCase(before)) {
            return;
        }
        
        String hint = getHint(table);

        Column pk[] = table.getPrimaryKeys();

        if (pk.length == 1 && pk[0].isColumnNumeric())
        {
            Column pkc = pk[0];
            indent(4, "if (!pObject." + getModifiedMethod(pkc) + "())");
            indent(4, "{");
            indent(5, "ps = c.prepareStatement(\""+hint+"\");");
            indent(5, "ResultSet rs = null;");
            indent(5, "try");
            indent(5, "{");
            indent(5, "    rs = ps.executeQuery();");
            indent(5, "    if(rs.next())");
            indent(5, "        pObject." + getSetMethod(pkc) + "(" + pkc.getResultSetMethodObject("1") + ");");
            indent(5, "    else");
            indent(5, "        getManager().log(\"ATTENTION: Could not retrieve generated key!\");");
            indent(5, "}");
            indent(5, "finally");
            indent(5, "{");
            indent(5, "    getManager().close(ps, rs);");
            indent(5, "    ps=null;");
            indent(5, "}");
            indent(4, "}");
        }
    }

    private String getHint(Table table) 
    {
        String hint = Main.getProperty("generatedkey.statement", "");
        
        int index = hint.indexOf("<TABLE>");
        if (index>0) {
            String tmp = hint.substring(0, index) + table.getName();
            
            if (hint.length() > index+"<TABLE>".length()) {
                tmp = tmp + hint.substring(index+"<TABLE>".length(), hint.length());
            }
            
            hint = tmp;
        }
        return hint;
    }
    
    private void writePrimaryKeyMethods() {
        // setup primary key strings
    	String beanClass = generateBeanClassName();    	
    	Column pk[] = table.getPrimaryKeys();    	
        StringBuffer keys = new StringBuffer();
        for(int i = 0; i < pk.length; i++)
        {
            if(i != 0)
            {
                keys.append(", ");
            }

            keys.append(pk[i].getJavaType());
            keys.append(" ");
            keys.append(getVarName(pk[i]));
        }

        String noWhereSelect = "SELECT \" + ALL_FIELDS + \" FROM " + table.getName();
        String baseSelect = noWhereSelect + " WHERE ";
        StringBuffer sql = new StringBuffer(baseSelect);

        if (pk.length != 0)
        {
            indent(1, "//////////////////////////////////////");
            indent(1, "// PRIMARY KEY METHODS");
            indent(1, "//////////////////////////////////////");

            // getInstance
            indent(0, "");
            indent(1, "/**");
            indent(1, " * Loads a " + beanClass + " from the " + table.getName() + " using its key fields.");
            indent(1, " *");
            indent(1, " * @return a unique " + beanClass + " ");
            indent(1, " */");
            for(int i = 0; i < pk.length; i++)
            {
                if(i > 0) sql.append(" and ");
                sql.append(pk[i].getFullName());
                sql.append("=?");
            }
            indent(1, "//12");
            indent(1, "public " + beanClass + " loadByPrimaryKey(" + keys + ") throws SQLException");
            indent(1, "{");
            indent(1, "    Connection c = null;");
            indent(1, "    PreparedStatement ps = null;");
            indent(1, "    try ");
            indent(1, "    {");
            indent(1, "        c = getConnection();");
            indent(1, "        ps = c.prepareStatement(\"" + sql + "\",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
            for(int i = 0; i < pk.length; i++)
            {
                indent(1, "        " + pk[i].getPreparedStatementMethod(getVarName(pk[i]), i+1));
            }
            indent(1, "        " + beanClass + " pReturn[] = loadByPreparedStatement(ps);");
            indent(1, "        if (pReturn.length < 1)");
            indent(1, "            return null;");
//            indent(1, "        else");
            indent(1, "        return pReturn[0];");
            indent(1, "    }");
            indent(1, "    finally");
            indent(1, "    {");
            indent(1, "        getManager().close(ps);");
            indent(1, "        freeConnection(c);");
            indent(1, "    }");
            indent(1, "}");
            indent(0, "");
       }

        // deleteByKey
        sql.setLength(0);
        sql.append("DELETE from ");
        sql.append(table.getName());
        sql.append(" WHERE ");
        for(int i = 0; i < pk.length; i++)
        {
            if(i > 0)
                sql.append(" and ");
            sql.append(pk[i].getFullName());
            sql.append("=?");
        }

        if (pk.length != 0)
        {
            indent(1, "/**");
            indent(1, " * Deletes rows according to its keys.");
            indent(1, " *");
            indent(1, " * @return the number of deleted rows");
            indent(1, " */");
            indent(1, "//60");
            indent(1, "public int deleteByPrimaryKey(" + keys + ") throws SQLException");
            indent(1, "{");
            indent(1, "    Connection c = null;");
            indent(1, "    PreparedStatement ps = null;");

            indent(1, "    try");
            indent(1, "    {");
            indent(1, "        "+beanClass+"[] deletedBeans = null;");
            indent(1, "        if (listeners.size() != 0) {");
            indent(1, "            deletedBeans = new "+beanClass+"[1];");
            StringBuffer vars = new StringBuffer();
            for(int i = 0; i < pk.length; i++) {
            	if (i != 0) {
            		vars.append(", ");
            	}
            	vars.append(getVarName(pk[i]));
            }
            indent(1, "            deletedBeans[0] = loadByPrimaryKey("+vars.toString()+");");
            indent(1, "            if (deletedBeans[0] == null) return 0;");
            indent(1, "        }");
            indent(1, "        beforeDelete(deletedBeans);");
            indent(1, "        c = getConnection();");
            indent(1, "        ps = c.prepareStatement(\"" + sql + "\",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
            for(int i = 0; i < pk.length; i++) {
                indent(1, "        " + pk[i].getPreparedStatementMethod(getVarName(pk[i]), i+1));
            }
            indent(1, "        int result = ps.executeUpdate();");
            indent(1, "        afterDelete(deletedBeans);");
            indent(1, "        return result;");
            indent(1, "    }");
            indent(1, "    finally");
            indent(1, "    {");
            indent(1, "        getManager().close(ps);");
            indent(1, "        freeConnection(c);");
            indent(1, "    }");
            indent(1, "}");
            indent(0, "");
        }
    	
    }
    
    private void writeForeignKeyMethods() {
        Column impKeys[] = table.getImportedKeys();
    	String beanClass = generateBeanClassName();
    	
        String noWhereSelect = "SELECT \" + ALL_FIELDS + \" FROM " + table.getName();
        String baseSelect = noWhereSelect + " WHERE ";
        StringBuffer sql = new StringBuffer(baseSelect);
        List<String> pImportedKeys = new ArrayList<String>();
        for(int i = 0; i < impKeys.length; i++)
        {
            if (i==0) {
                indent(1, "");
                indent(1, "");        
                indent(1, "//////////////////////////////////////");
                indent(1, "// FOREIGN KEY METHODS ");
                indent(1, "//////////////////////////////////////");
            }

            sql.setLength(0);
            sql.append(baseSelect);
            sql.append(impKeys[i].getForeignKeyColName()); // pointer name
            sql.append("=?");

            if (isStringInArrayList(pImportedKeys, impKeys[i].getForeignKeyColName()))
                continue;

            pImportedKeys.add(impKeys[i].getForeignKeyColName());

            String methodName = "loadBy" +   convertName(impKeys[i].getForeignKeyColName());
            String deleteMethodName = "deleteBy" +   convertName(impKeys[i].getForeignKeyColName());

            // loadByKey
            indent(0, "");
            indent(1, "/**");
            indent(1, " * Loads " + beanClass + " array from the " + table.getName() + " table using its " 
                   + impKeys[i].getForeignKeyColName() + " field.");
            indent(1, " *");
            indent(1, " * @return an array of " + beanClass + " ");
            indent(1, " */");
            indent(1, "// LOAD BY IMPORTED KEY");
            indent(1, "public " + beanClass + "[] " + methodName + "(" + impKeys[i].getJavaType() + " value) throws SQLException ");
            indent(1, "{");
            indent(1, "    Connection c = null;");
            indent(1, "    PreparedStatement ps = null;");
            indent(1, "    try ");
            indent(1, "    {");
            indent(1, "        c = getConnection();");
            indent(1, "        ps = c.prepareStatement(\"" + sql 
                   + "\",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
            indent(1, "        " + impKeys[i].getPreparedStatementMethod("value", 1));
            indent(1, "        return loadByPreparedStatement(ps);");
            indent(1, "    }");
            indent(1, "    finally");
            indent(1, "    {");
            indent(1, "        getManager().close(ps);");
            indent(1, "        freeConnection(c);");
            indent(1, "    }");
            indent(1, "}");
            indent(0, "");

            // If we can load we can also delete by...
            // 
            String delSQL="DELETE FROM " + table.getName() + " WHERE " + impKeys[i].getForeignKeyColName() + "=?";

            indent(0, "");
            indent(1, "/**");
            indent(1, " * Deletes from the " + table.getName() + " table by " + impKeys[i].getForeignKeyColName() + " field.");
            indent(1, " *");
            indent(1, " * @param value the key value to seek");
            indent(1, " * @return the number of rows deleted");
            indent(1, " */");
            indent(1, "// DELETE BY IMPORTED KEY");            
            indent(1, "public int " + deleteMethodName + "(" + impKeys[i].getJavaType() + " value) throws SQLException ");
            indent(1, "{");
            indent(1, "    Connection c = null;");
            indent(1, "    PreparedStatement ps = null;");
            indent(1, "    try ");
            indent(1, "    {");
            indent(1, "        "+beanClass+"[] deletedBeans = null;");
            indent(1, "        if (listeners.size() != 0) {");
            indent(1, "            deletedBeans = "+ methodName +"(value);");
            indent(1, "        }");
            indent(1, "        beforeDelete(deletedBeans);");
            indent(1, "        c = getConnection();");
            indent(1, "        ps = c.prepareStatement(\"" + delSQL + "\");");
            indent(1, "        " + impKeys[i].getPreparedStatementMethod("value", 1));
            indent(1, "        int result = ps.executeUpdate();");
            indent(1, "        afterDelete(deletedBeans);");
            indent(1, "        return result;");
            indent(1, "    }");
            indent(1, "    finally");
            indent(1, "    {");
            indent(1, "        getManager().close(ps);");
            indent(1, "        freeConnection(c);");
            indent(1, "    }");
            indent(1, "}");
            indent(0, "");
        }
    }
    
    private void writeForeignKeyGetterAndSetter() {
        Column impKeys[] = table.getImportedKeys();
    	String beanClass = generateBeanClassName();
        List<String> pLoadBy = new ArrayList<String>();
        for(int i = 0; i < impKeys.length; i++)
        {
            if (i==0) {
                indent(0, "");
                indent(0, "");        
                indent(1, "//////////////////////////////////////");
                indent(1, "// GET/SET FOREIGN KEY BEAN METHOD");
                indent(1, "//////////////////////////////////////");
            }

            String importedClass = generateBeanClassName(impKeys[i].getTableName());
            String importedClassManager = generateManagerClassName(impKeys[i].getTableName());

            if (pLoadBy.contains(importedClass))
                continue;
            pLoadBy.add(importedClass);

            System.out.println(impKeys[i].getForeignKeyTabName() + "." + impKeys[i].getForeignKeyColName() + " -> " + impKeys[i].getTableName() + "." + impKeys[i].getName() );
            Column pForeignColumn = impKeys[i].getForeignColumn();

            // get foreign Class
            indent(1, "/**");
            indent(1, " * Retrieves the " + importedClass + " object from the " + table.getName() + 
                   "." + impKeys[i].getName() + " field.");
            indent(1, " *");
            indent(1, " * @param pObject the " + beanClass + " ");
            indent(1, " * @return the associated " + importedClass + " pObject");
            indent(1, " */");
            indent(1, "// GET IMPORTED");
            indent(1, "public " + importedClass + " get" + importedClass+ "(" + beanClass + " pObject) throws SQLException");
            indent(1, "{");
            indent(1, "    " + importedClass + " other = " + importedClassManager + 
                   ".getInstance().create"+importedClass+"();");
            indent(1, "    other." + getSetMethod(impKeys[i]) + 
                   "(pObject."+getGetMethod(impKeys[i].getForeignColumn()) +"());");
            indent(1, "    return " + generateManagerClassName(impKeys[i].getTableName()) + 
                   ".getInstance().loadUniqueUsingTemplate(other);");
            indent(1, "}");
            indent(0, "");

            // set foreign key object
            indent(1, "/**");
            indent(1, " * Associates the " + beanClass + " object to the " + importedClass + " object.");
            indent(1, " *");
            indent(1, " * @param pObject the " + beanClass + " object to use");
            indent(1, " * @param pObjectToBeSet the " + importedClass + " object to associate to the " + beanClass + " ");
            indent(1, " * @return the associated " + importedClass + " pObject");
            indent(1, " */");
            indent(1, "// SET IMPORTED");
            indent(1, "public " + beanClass + " set" + importedClass+ "(" + beanClass + " pObject," + importedClass + " pObjectToBeSet)");
            indent(1, "{");
            indent(1, "    pObject." + getSetMethod(pForeignColumn) + "(pObjectToBeSet." + getGetMethod(impKeys[i]) + "());");
            indent(1, "    return pObject;");
            indent(1, "}");
            indent(0, "");
        }
        pLoadBy.clear();

//         indent(1, "");
//         indent(1, "");        
//         indent(1, "//////////////////////////////////////");
//         indent(1, "// GET/SET IMPORTED");
//         indent(1, "//////////////////////////////////////");

//         // write load methods for all many-to-many relationships
//         Column manyToMany[][] = table.getManyToManyKeys();
//         for(int i = 0; i < manyToMany.length; i++) {
//             // This is the key in the cross-ref table
//             Column fk = manyToMany[i][0];

//             // This is the key in our table (probably our primary key)
//             Column mmPk = manyToMany[i][1];

//             // Get the other columns in the cross ref table
//             Table fkTable = db.getTable(fk.getTableName());

//             sql.setLength(0);
//             sql.append(noWhereSelect);
//             sql.append(", ");
//             sql.append(fkTable.getName());
//             sql.append(" WHERE ");
//             sql.append(table.getName() + "." + mmPk.getName() + "=");
//             sql.append(fk.getTableName() + "." + fk.getName());

//             Column fkCol[] = fkTable.getColumns();
//             StringBuffer otherCols = null;
//             StringBuffer argList = null;
//             StringBuffer argNames = null;
//             for(int x = 0; x < fkCol.length; x++) {
//                 if(!fkCol[x].getName().equals(fk.getName())) {
//                     sql.append(" AND ");
//                     sql.append(fkTable.getName() + "." + fkCol[x].getName());
//                     sql.append("=?");

//                     if(argList == null) argList = new StringBuffer();
//                     else                argList.append(", ");
//                     argList.append(getJavaType(fkCol[x]) + " " +
//                                    convertName(fkCol[x].getName()));

//                     if(argNames == null) argNames = new StringBuffer();
//                     else                 argNames.append(", ");
//                     argNames.append(convertName(fkCol[x].getName()));

//                     if(otherCols == null) otherCols = new StringBuffer(fkTable.getName() + "_");
//                     else                  otherCols.append("_and_");
//                     otherCols.append(fkCol[x].getName());
//                 }
//             }

//             String methodName = "loadBy" + convertName(otherCols.toString());

//             indent(1, "//36");
//             indent(1, "public " + beanClass + "[] " + methodName + "(" + argList + ") throws SQLException");
//             indent(1, "{");
//             indent(1, "    Connection c = null;");
//             indent(1, "    PreparedStatement ps = null;");
//             indent(1, "    try ");
//             indent(1, "    {");
//             indent(1, "        c = getConnection();");
//             indent(1, "        ps = c.prepareStatement(\"" + sql + "\",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
//             int z = 1;
//             for(int x = 0; x < fkCol.length; x++) {
//                 if(!fkCol[x].getName().equals(fk.getName())) {
//                     indent(1, "        " + getPreparedStatementMethod(fkCol[x], convertName(fkCol[x].getName()), z));
//                     z++;
//                 }
//             }
//             indent(1, "        return loadByPreparedStatement(ps);");

//             indent(1, "    }");
//             indent(1, "    finally");
//             indent(1, "    {");
//             indent(1, "        getManager().close(ps);");
//             indent(1, "        freeConnection(c);");
//             indent(1, "    }");
//             indent(1, "}");
//             indent(1, "");
//         }
    	
    }
    
    private void writeAllMethods() {
    	
    	String beanClass = generateBeanClassName();
        String noWhereSelect = "SELECT \" + ALL_FIELDS + \" FROM " + table.getName();
    	
        indent(0, "");
        indent(0, "");        
        indent(1, "//////////////////////////////////////");
        indent(1, "// LOAD ALL");
        indent(1, "//////////////////////////////////////");
        // loadAll
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Loads all the rows from " + table.getName() + ".");
        indent(1, " *");
        indent(1, " * @return an array of " + className + " pObject");
        indent(1, " */");
        indent(1, "//38");
        indent(1, "public " + beanClass + "[] loadAll() throws SQLException ");
        indent(1, "{");
        indent(1, "    Connection c = null;");
        indent(1, "    PreparedStatement ps = null;");
        indent(1, "    try ");
        indent(1, "    {");
        indent(1, "        c = getConnection();");
        indent(1, "        ps = c.prepareStatement(\"" + noWhereSelect + "\",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
        indent(1, "        return loadByPreparedStatement(ps);");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(ps);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");

        indent(1, "//////////////////////////////////////");
        indent(1, "// SQL 'WHERE' METHOD");
        indent(1, "//////////////////////////////////////");
        indent(1, "/**");
        indent(1, " * Retrieves an array of " + beanClass + " given a sql 'where' clause.");
        indent(1, " *");
        indent(1, " * @param where the sql 'where' clause");
        indent(1, " * @return the resulting " + beanClass + " table ");
        indent(1, " */");
        indent(1, "//49");
        indent(1, "public " + beanClass + "[] loadByWhere(String where) throws SQLException");
        indent(1, "{");
        indent(1, "    return loadByWhere(where, null);");
        indent(1, "}");
        indent(0, "");

        indent(1, "/**");
        indent(1, " * Retrieves an array of " + beanClass + " given a sql where clause, and a list of fields.");
        indent(1, " * It is up to you to pass the 'WHERE' in your where clausis.");
        indent(1, " *");
        indent(1, " * @param where the sql 'where' clause");
        indent(1, " * @param fieldList table of the field's associated constants");
        indent(1, " * @return the resulting " + beanClass + " table ");
        indent(1, " */");
        indent(1, "//51");
        indent(1, "public " + beanClass + "[] loadByWhere(String where, int[] fieldList) throws SQLException");
        indent(1, "{");
        indent(1, "    String sql = null;");
        indent(1, "    if(fieldList == null)");
        indent(1, "        sql = \"select \" + ALL_FIELDS + \" from " + table.getName() + " \" + where;");
        indent(1, "    else");
        indent(1, "    {");
        indent(1, "        StringBuffer buff = new StringBuffer(128);");
        indent(1, "        buff.append(\"select \");");
        indent(1, "        for(int i = 0; i < fieldList.length; i++)");
        indent(1, "        {");
        indent(1, "            if(i != 0)");
        indent(1, "                buff.append(\",\");");
        indent(1, "            buff.append(FIELD_NAMES[fieldList[i]]);");
        indent(1, "        }");
        indent(1, "        buff.append(\" from " + table.getName() + " \");");
        indent(1, "        buff.append(where);");
        indent(1, "        sql = buff.toString();");
        indent(1, "        buff = null;");
        indent(1, "    }");
        indent(1, "    Connection c = null;");
        indent(1, "    Statement pStatement = null;");
        indent(1, "    ResultSet rs =  null;");
        indent(1, "    java.util.List<"+beanClass+"> v = null;");
        indent(1, "    try ");
        indent(1, "    {");
        indent(1, "        c = getConnection();");
        indent(1, "        pStatement = c.createStatement();");
        indent(1, "        rs =  pStatement.executeQuery(sql);");
        indent(1, "        v = new java.util.ArrayList<"+beanClass+">();");
        indent(1, "        while(rs.next())");
        indent(1, "        {");
        indent(1, "            if(fieldList == null)");
        indent(1, "                v.add(decodeRow(rs));");
        indent(1, "            else");
        indent(1, "                v.add(decodeRow(rs, fieldList));");
        indent(1, "        }");
        indent(0, "");
        indent(1, "        return v.toArray(new " + beanClass + "[0]);");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        if (v != null) { v.clear(); }");
        indent(1, "        getManager().close(pStatement, rs);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
        indent(0, "");
		indent(1, "/**");
		indent(1, " * Deletes all rows from " + table.getName() + " table.");
		indent(1, " * @return the number of deleted rows.");
		indent(1, " */");        
		indent(1, "public int deleteAll() throws SQLException");
		indent(1, "{");
		indent(1, "    return deleteByWhere(\"\");");
		indent(1, "}");
		indent(0, "");
		indent(0, "");                  
        indent(1, "/**");
        indent(1, " * Deletes rows from the " + table.getName() + " table using a 'where' clause.");
		indent(1, " * It is up to you to pass the 'WHERE' in your where clausis.");
		indent(1, " * <br>Attention, if 'WHERE' is omitted it will delete all records. ");
        indent(1, " *");
        indent(1, " * @param where the sql 'where' clause");
        indent(1, " * @return the number of deleted rows");
        indent(1, " */");        
        indent(1, "public int deleteByWhere(String where) throws SQLException");
        indent(1, "{");
        indent(1, "    Connection c = null;");
        indent(1, "    PreparedStatement ps = null;");
		indent(0, ""); 
        indent(1, "    try");
        indent(1, "    {");
        indent(1, "        "+beanClass+"[] deletedBeans = null;");
        indent(1, "        if (listeners.size() != 0) {");
        indent(1, "            deletedBeans = loadByWhere(where);");
        indent(1, "        }");
        indent(1, "        beforeDelete(deletedBeans);");
        indent(1, "        c = getConnection();");
        indent(1, "        String delByWhereSQL = \"DELETE FROM " + table.getName() + " \" + where;");
        indent(1, "        ps = c.prepareStatement(delByWhereSQL);");
        indent(1, "        int result = ps.executeUpdate();");
        indent(1, "        afterDelete(deletedBeans);");
        indent(1, "        return result;");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(ps);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
    	
    }
    
    private void writeSaveMethods() {
    	
    	String beanClass = generateBeanClassName();

    	indent(0, "");
        indent(0, "");
        indent(1, "///////////////////////////////////////////////////////////////////////");
        indent(1, "// SAVE ");
        indent(1, "///////////////////////////////////////////////////////////////////////");

        writeSave();

        indent(0, "");
        indent(0, "");
        indent(1, "/**");
        indent(1, " * Saves an array of " + beanClass + " pObjects into the database.");
        indent(1, " *");
        indent(1, " * @param pObjects the " + beanClass + " pObject table to be saved");
        indent(1, " * @return the saved " + beanClass + " array.");
        // TODO: BATCH UPDATE
        indent(1, " */");
        indent(1, "//65");
        indent(1, "public " + beanClass + "[] save(" + beanClass + "[] pObjects) throws SQLException ");
        indent(1, "{");
        indent(1, "    for (int iIndex = 0; iIndex < pObjects.length; iIndex ++){");
        indent(1, "        save(pObjects[iIndex]);");
        indent(1, "    }");
        indent(1, "    return pObjects;");
        indent(1, "}");
        indent(0, "");
    	
    }
    
    private void writeLoadUsingTemplateMethods() {
    	
    	String beanClass = generateBeanClassName();
        Column cols[] = table.getColumns();
    	
        indent(0, "");
        indent(0, "");
        indent(1, "///////////////////////////////////////////////////////////////////////");
        indent(1, "// USING TEMPLATE ");
        indent(1, "///////////////////////////////////////////////////////////////////////");

        // loadObject
        indent(1, "/**");
        indent(1, " * Loads a unique " + beanClass + " pObject from a template one giving a c");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " pObject to look for");
        indent(1, " * @return the pObject matching the template");
        indent(1, " */");
        indent(1, "//85");
        indent(1, "public " + beanClass + " loadUniqueUsingTemplate(" + beanClass + " pObject) throws SQLException");
        indent(1, "{");
        indent(1, "     " + beanClass + "[] pReturn = loadUsingTemplate(pObject);");
        indent(1, "     if (pReturn.length == 0)");
        indent(1, "         return null;");
        indent(1, "     if (pReturn.length > 1)");
        indent(1, "         throw new SQLException(\"More than one element !!\");");
        indent(1, "     return pReturn[0];");
        indent(1, " }");
        indent(0, "");

        // loadObjects 
        indent(1, "/**");
        indent(1, " * Loads an array of " + beanClass + " from a template one.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " template to look for");
        indent(1, " * @return all the " + beanClass + " matching the template");
        indent(1, " */");
        indent(1, "//88");
        indent(1, "public " + beanClass + "[] loadUsingTemplate(" + beanClass + " pObject) throws SQLException");
        indent(1, "{");
        indent(1, "    Connection c = null;");
        indent(1, "    PreparedStatement ps = null;");
//        indent(1, "    StringBuffer where = new StringBuffer(\"\");");
        indent(1, "    StringBuffer _sql = new StringBuffer(\"SELECT \" + ALL_FIELDS + \" from " + table.getName() + " WHERE \");");
        indent(1, "    StringBuffer _sqlWhere = new StringBuffer(\"\");");
        indent(1, "    try");
        indent(1, "    {");
        indent(1, "        int _dirtyCount = 0;");
        for(int i = 0; i < cols.length; i++)
        {
			  indent(1, "");
              indent(1, "         if (pObject." + getModifiedMethod(cols[i]) + "()) {");
              indent(1, "             _dirtyCount ++; ");
              indent(1, "             _sqlWhere.append((_sqlWhere.length() == 0) ? \" \" : \" AND \");");
              indent(1, "             if (pObject." + getGetMethod(cols[i]) + "() == null) {");
              indent(1, "             	_sqlWhere.append(\"" + cols[i].getName() + " IS NULL\");");
              indent(1, "             } else { ");
              indent(1, "             	_sqlWhere.append(\"" + cols[i].getName() + " = ?\");");
           	  indent(1, "             }");
              indent(1, "         }");
        }
		indent(1, "");		
        indent(1, "         if (_dirtyCount == 0) {");
        indent(1, "             throw new SQLException (\"The pObject to look for is invalid : not initialized !\");");
		indent(1, "         }");
		
        indent(1, "         _sql.append(_sqlWhere);");
        indent(1, "         c = getConnection();");
        indent(1, "         ps = c.prepareStatement(_sql.toString(),ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
        indent(1, "         _dirtyCount = 0;");
        for(int i = 0; i < cols.length; i++)
        {
		indent(1, "");	
        indent(1, "         if (pObject." + getModifiedMethod(cols[i]) + "() && pObject."+ getGetMethod(cols[i])+"() != null) {");
        indent(1, "             " + cols[i].getPreparedStatementMethod(
                                "pObject."+getGetMethod(cols[i])+"()", "++_dirtyCount"));
		indent(1, "         }");
        }
		indent(1, "");
        indent(1, "         ps.executeQuery();");
        indent(1, "         return loadByPreparedStatement(ps);");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(ps);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
    }
    
    private void writeDeleteUsingTemplateMethods() {
    	
    	String beanClass = generateBeanClassName();
        Column cols[] = table.getColumns();
        Column pk[] = table.getPrimaryKeys();
    	
        // delete
        indent(1, "/**");
        indent(1, " * Deletes rows using a " + beanClass + " template.");
        indent(1, " *");
        indent(1, " * @param pObject the " + beanClass + " object(s) to be deleted");
        indent(1, " * @return the number of deleted objects");
        indent(1, " */");
        indent(1, "//63");
        indent(1, "public int deleteUsingTemplate(" + beanClass + " pObject) throws SQLException");
        indent(1, "{");
        if (pk.length == 1)
        {
            indent(1, "    if (pObject." + getInitializedMethod(pk[0])+ "())");
            indent(1, "        return deleteByPrimaryKey(pObject." + getGetMethod(pk[0])+ "());");
			indent(1, "");
        }
        indent(1, "    Connection c = null;");
        indent(1, "    PreparedStatement ps = null;");
        indent(1, "    StringBuffer sql = null;");
		indent(1, "");
        indent(1, "    try ");
        indent(1, "    {");
        indent(1, "        sql = new StringBuffer(\"DELETE FROM " + table.getName() + " WHERE \");");
        indent(1, "        int _dirtyAnd = 0;");
        for(int i = 0; i < cols.length; i++)
        {
          indent(1, "        if (pObject." + getInitializedMethod(cols[i]) + "()) {");
          indent(1, "            if (_dirtyAnd > 0)");
          indent(1, "                sql.append(\" AND \");");
          indent(1, "            sql.append(\"" + cols[i].getName() + "\").append(\"=?\");");
          indent(1, "            _dirtyAnd ++;");
          indent(1, "        }");
		  indent(1, "");
        }
        indent(1, "        "+beanClass+"[] deletedBeans = null;");
        indent(1, "        if (listeners.size() != 0) {");
        indent(1, "            deletedBeans = loadUsingTemplate(pObject);");
        indent(1, "        }");
        indent(1, "        beforeDelete(deletedBeans);");
        indent(1, "        c = getConnection();");
        indent(1, "        ps = c.prepareStatement(sql.toString(),ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);");
        indent(1, "        int _dirtyCount = 0;");
        for(int i = 0; i < cols.length; i++)
        {
		  indent(1, "");
          indent(1, "        if (pObject." + getInitializedMethod(cols[i]) + "()) {");
          indent(4, cols[i].getPreparedStatementMethod(
                     "pObject."+getGetMethod(cols[i])+"()",
                     "++_dirtyCount"));
		  indent(1, "        }");
        }
		indent(1, "");
        indent(1, "        int result = ps.executeUpdate();");
        indent(1, "        afterDelete(deletedBeans);");
        indent(1, "        return result;");
        indent(1, "    }");
        indent(1, "    finally");
        indent(1, "    {");
        indent(1, "        getManager().close(ps);");
        indent(1, "        freeConnection(c);");
        indent(1, "    }");
        indent(1, "}");
        indent(0, "");
    }
    
    /**
     * Write the cache control
     */
    private void writeCacheControl() {
    	indent(1, "private boolean useCache = false;");
    	indent(1, "private Cache cache = null;");
    	indent(0, "");

    	if (cachingTables != null && cachingTables.containsKey(table.getName()) == true) {
	    	indent(1, "{");
	    	indent(1, "	enableCache();");
	    	indent(1, "}");
	    	indent(0, "");
    	}

    	indent(1, "/**");
    	indent(1, " * Enables the cache. Any calls to the cache functions before");
    	indent(1, " * enabling them will result in an IllegalStateException");
    	indent(1, " */");
    	indent(1, "public void enableCache() {");
    	indent(1, "	useCache = true;");
    	indent(1, "	cache = new Cache();");
    	indent(1, "}");
    	indent(0, "");
    	indent(1, "/**");
    	indent(1, " * Disables the cache. Any calls to the cache functions after"); 
    	indent(1, " * disabling them will result in an IllegalStateException");
    	indent(1, " */");
    	indent(1, "public void disableCache() {");
    	indent(1, "	useCache = false;");
    	indent(1, "	removeListener(cache);");
    	indent(1, "	cache = null;");
    	indent(1, "}");
    	indent(0, "");
    	indent(1, "public " + generateBeanClassName() + " getByPrimaryKey("+ getMethodParameters(table.getPrimaryKeys()) +") {");
    	indent(1, "	if (useCache == false) {");
    	indent(1, "		throw new IllegalStateException(\"Caching for "+ generateManagerClassName() +" is not enabled\");");
    	indent(1, "	}");
    	indent(1, "	return cache.get(" + getCallerArguments(table.getPrimaryKeys()) +");");
    	indent(1, "}");
    }

    /**
     * Write the inner cache class
     */
    private void writeCacheClass() {
        indent(1, "/**");
        indent(1, " * The cache class for the " + generateManagerClassName() + " template.");
        indent(1, " */");
    	indent(1, "private class Cache implements " + generateListenerClassName() +" {");
        indent(0, "");
        indent(2, "private Map<" + generateKeyClassName() + ", " + generateBeanClassName() +"> cache = new ConcurrentHashMap<" + generateKeyClassName() + ", " + generateBeanClassName() +">();");
        indent(0, "");
        indent(2, "public Cache() {");
        indent(2, "    addListener(this);");
        indent(2, "}");
        indent(0, "");
        indent(2, "private "+ generateBeanClassName() +" get(" +getMethodParameters(table.getPrimaryKeys())+") {");
        indent(2, "    " + generateKeyClassName() + " key = new "+ generateKeyClassName() +"("+ getCallerArguments(table.getPrimaryKeys()) +");");
        indent(2, "    if (cache.containsKey(key) == false) {");
        indent(2, "        try {");
        indent(2, "           " + generateBeanClassName() + " value = loadByPrimaryKey(" + getCallerArguments(table.getPrimaryKeys()) + ");");
        indent(2, "           if (value == null) return null;");
        indent(2, "           cache.put(key, value);");
        indent(2, "        } catch (SQLException e) { }");
        indent(2, "    }");
        indent(2, "    return cache.get(key);");
        indent(2, "}");
        indent(0, "");
        indent(2, "/* (non-Javadoc)");
        indent(2, "* @see "+ basePackage+generateListenerClassName() + "#beforeInsert("+basePackage+generateBeanClassName()+")");
        indent(2, "*/");
        indent(2, "public void beforeInsert("+ generateBeanClassName() +" pObject) throws SQLException {");
        indent(2, "}");
        indent(0, "");
        indent(2, "/* (non-Javadoc)");
        indent(2, "* @see "+ basePackage+generateListenerClassName() + "#afterInsert("+basePackage+generateBeanClassName()+")");
        indent(2, "*/");
        indent(2, "public void afterInsert(" + generateBeanClassName() +" pObject) throws SQLException {");
        indent(2, "    cache.put(pObject.getKey(), pObject);");
        indent(2, "}");
        indent(0, "");
        indent(2, "/* (non-Javadoc)");
        indent(2, "* @see "+ basePackage+generateListenerClassName() + "#beforeUpdate("+basePackage+generateBeanClassName()+")");
        indent(2, "*/");
        indent(2, "public void beforeUpdate("+ generateBeanClassName() +" pObject) throws SQLException {");
        indent(2, "}");
        indent(0, "");
        indent(2, "/* (non-Javadoc)");
        indent(2, "* @see "+ basePackage+generateListenerClassName() + "#afterUpdate("+basePackage+generateBeanClassName()+")");
        indent(2, "*/");
        indent(2, "public void afterUpdate(" + generateBeanClassName() +" pObject) throws SQLException {");
        indent(2, "    cache.put(pObject.getKey(), pObject);");
        indent(2, "}");
        indent(0, "");
        indent(2, "/* (non-Javadoc)");
        indent(2, "* @see "+ basePackage+generateListenerClassName() + "#beforeDelete("+basePackage+generateBeanClassName()+")");
        indent(2, "*/");
        indent(2, "public void beforeDelete("+ generateBeanClassName() +"[] beans) throws SQLException {");
        indent(2, "}");
        indent(0, "");
        indent(2, "/* (non-Javadoc)");
        indent(2, "* @see "+ basePackage+generateListenerClassName() + "#afterDelete("+basePackage+generateBeanClassName()+")");
        indent(2, "*/");
        indent(2, "public void afterDelete(" + generateBeanClassName() +"[] deletedBeans) throws SQLException {");
        indent(2, "    for ("+ generateBeanClassName() +" element : deletedBeans) {");
        indent(2, "        cache.remove(element.getKey());");
        indent(2, "    }");
        indent(2, "}");
        indent(1, "}");
        indent(0, "");
    }
}

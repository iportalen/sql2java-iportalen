//$Id: Database.java,v 1.1 2005-10-20 19:43:22 fbr Exp $

package com.netkernel.sql2java;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Vector;

public class Database
{
    private String tableTypes[];
    private Connection pConnection;
    private DatabaseMetaData meta;
    private Vector<Table> tables;
    private Hashtable<String, Table> tableHash;
    private String driver, url, username, password, catalog, schema, tablenamepattern;

    public void setDriver(String driver) { this.driver = driver; }
    public void setURL(String url) { this.url = url; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setCatalog(String catalog) { this.catalog = catalog; }
    public void setSchema(String schema) { this.schema = schema; }
    public void setTableNamePattern(String tablenamepattern) { this.tablenamepattern = tablenamepattern; }
    public void setTableTypes(String[] tt) { this.tableTypes = tt; }

    public String getDriver() { return driver; }

    public String getURL() { return url; }

    public String getUsername() { return username; }    

    public String getPassword() { return password; }

    public String getCatalog() { return catalog; }

    public String getSchema() { return schema; }

    public String getTableNamePattern() { return tablenamepattern; }

    public String[] getTableTypes() { return tableTypes; }


    /**
     * Return an array of tables having foreign key pointing to the
     * passed table.
     */
    public Table[] getRelationTable(Table pTable)
    {
        Vector<Table> pVector = new Vector<Table>();

        for (int iIndex = 0; iIndex < tables.size(); iIndex ++)            
        {            
            Table pTempTable = tables.get(iIndex);            

            // skip itself            
            if (pTable.equals(pTempTable))                
                continue;

            // check only for relation table            
            if (pTempTable.isRelationTable())                
            {                
                if (pTempTable.relationConnectsTo(pTable))                    
                {                    
                    if (!pVector.contains(pTempTable))                        
                        pVector.add(pTempTable);                    
                }
            }        
        }
        
        Table pReturn[] = new Table[pVector.size()];        
        pVector.copyInto(pReturn);        
        return pReturn;        
    }

    public void load() throws SQLException, ClassNotFoundException    
    {        
        // Connect to the database                       
        Class.forName(driver);

        pConnection = DriverManager.getConnection(url, username, password);        
        meta = pConnection.getMetaData();

        System.out.println("DATABASE: " + meta.getDatabaseProductName());
        tables = new Vector<Table>();
        tableHash = new Hashtable<String, Table>();

        loadTables();
        loadColumns();
        loadPrimaryKeys();
        loadImportedKeys();
        loadManyToMany();
//		loadIndexes(); // experimental
    }

    public Table[] getTables()
    {
        Table list[] = new Table[tables.size()];
        tables.copyInto(list);
        return list;
    }

    private void addTable(Table t) {
        tables.addElement(t);
        tableHash.put(t.getName(), t);
    }

    public Table getTable(String name) {
        return tableHash.get(name);
    }

    /**
     * Load all the tables for this schema.
     */
    private void loadTables() throws SQLException
    {
        System.out.println("Database::loadTables - " + tablenamepattern);
        ResultSet pResultSet =  meta.getTables(catalog,
                                               schema,
                                               (tablenamepattern==null?"SLP%":tablenamepattern),
                                               tableTypes);

        while(pResultSet.next())            
        {            
            Table table = new Table();            
            table.setCatalog(pResultSet.getString("TABLE_CAT"));            
            table.setSchema(pResultSet.getString("TABLE_SCHEM"));            
            table.setName(pResultSet.getString("TABLE_NAME"));            
            table.setType(pResultSet.getString("TABLE_TYPE"));            

            //table.setRemarks(pResultSet.getString("REMARKS"));                                    
            addTable(table);            
            System.out.println("Database::loadTables: loaded " + table.getName());            
        }

        pResultSet.close();        
    }

    /**
     * For each table, load all the columns.
     */
    private void loadColumns() throws SQLException    
    {        
        System.out.println("Database::loadColumns");        
        Table tables[] = getTables();

        for(int i = 0; i < tables.length; i++)
        {
            Table table = tables[i];
            ResultSet pResultSet =  meta.getColumns(catalog, schema, table.getName(), "%");
            System.out.println("Database::loadColumns: for table " + table.getName());
            Column c = null;

            while(pResultSet.next())
            {
                c = new Column();
                c.setDatabase(this);
                c.setCatalog(pResultSet.getString("TABLE_CAT"));
                c.setSchema(pResultSet.getString("TABLE_SCHEM"));
                String strTableName = pResultSet.getString("TABLE_NAME");
                c.setTableName(strTableName);
                String strFieldName = pResultSet.getString("COLUMN_NAME");
                c.setName(strFieldName);
                c.setType(pResultSet.getShort("DATA_TYPE"));
                c.setSize(pResultSet.getInt("COLUMN_SIZE"));
                c.setDecimalDigits(pResultSet.getInt("DECIMAL_DIGITS"));
                c.setRadix(pResultSet.getInt("NUM_PREC_RADIX"));
                c.setNullable(pResultSet.getInt("NULLABLE"));
                c.setRemarks(pResultSet.getString("REMARKS"));
                c.setDefaultValue(pResultSet.getString("COLUMN_DEF"));
                c.setOrdinalPosition(pResultSet.getInt("ORDINAL_POSITION"));

                //System.out.println("Database::loadColumns: for table " + table.getName() + " " + strFieldName);
                //System.out.println(c.toString());                                
                table.addColumn(c);                

                // System.out.println("Database::loadColumns: for table " + table.getName() + " field " + c.getName());
            }

            pResultSet.close();
        }
    }

    /**
     * For each table, load the primary keys.
     */
    private void loadPrimaryKeys() throws SQLException
    {
        System.out.println("Database::loadPrimaryKeys");        
        Table tables[] = getTables();

        for(int i = 0; i < tables.length; i++)            
        {            
            Table table = tables[i];            
            ResultSet pResultSet =  meta.getPrimaryKeys(catalog, schema, table.getName());            

            while(pResultSet.next())
            {                
                String colName = pResultSet.getString("COLUMN_NAME");
                System.out.println("Found primary key " + colName + " for table " +  table.getName());                
                Column col = table.getColumn(colName);

                if(col != null)                    
                {                    
                    table.addPrimaryKey(col);                    
                }                
            }

            pResultSet.close();
        }
    }

    /**
     * For each table, load the imported key.
     * <br>
     * An imported key is the other's table column clone. Its
     * ForeignKeyColName corresponds to the table's column name that
     * points to the other's table.
     */
    private void loadImportedKeys() throws SQLException
    {
        System.out.println("Database::loadImportedKeys");
        Table tables[] = getTables();

        for(int i = 0; i < tables.length; i++)
        {
            Table table = tables[i];
            ResultSet pResultSet =  meta.getImportedKeys(catalog, schema, table.getName());
            System.out.println("Table: "+table.getName()+": looking for imported keys...");

            while(pResultSet.next())
            {
                String tabName = pResultSet.getString("PKTABLE_NAME");
                String colName = pResultSet.getString("PKCOLUMN_NAME");
                String foreignColName = pResultSet.getString("FKCOLUMN_NAME");
                String foreignTableName = pResultSet.getString("FKTABLE_NAME");

                System.out.println("Table: "+table.getName()+": Found imported key " + foreignColName + " at " + foreignTableName + 
                                   " on " +  tabName + "." + colName);// + " (foreign name is " + foreignColName + ")");

                Table otherTable = getTable(tabName);

                if(otherTable != null)
                {
                    Column otherCol = null;
                    try {
                        otherCol = (Column)otherTable.getColumn(colName).clone(); // clone because we are not alone
                    } catch (CloneNotSupportedException cnse) {
                        throw new SQLException(""+cnse.toString());
                    }

                    otherCol.setForeignKeyColName(foreignColName);                    
                    otherCol.setForeignKeyTabName(foreignTableName);

                    if(otherCol != null)
                    {
                        table.addImportedKey(otherCol);
                    }
                    else
                    {
                        System.out.println("Not foreign field !!!");
                    }
                }
            }
            
            pResultSet.close();
        }
    }

    //
    // could avoid db call.
    // In each current table an entry is: 
    //    other table column (that points to current table) | current pk column  
    //     [other table has nb col. == pk length]
    private void loadManyToMany() throws SQLException
    {        
        System.out.println("Database::loadManyToMany");
        Table tables[] = getTables();

        for(int i = 0; i < tables.length; i++)            
        {            
            Table table = tables[i];

            if(table.getColumns().length == table.getPrimaryKeys().length)                
            {                
                ResultSet pResultSet =  meta.getImportedKeys(catalog, schema, table.getName());

                while(pResultSet.next())
                {
                    String tabName = pResultSet.getString("PKTABLE_NAME");
                    String colName = pResultSet.getString("PKCOLUMN_NAME");
                    System.out.println(" many to many " + tabName + " " + colName);

                    Table pkTable = getTable(tabName);
                    Column fkCol = table.getColumn(pResultSet.getString("FKCOLUMN_NAME"));

                    if(pkTable != null)
                    {
                        Column pkCol = pkTable.getColumn(colName);

                        if(pkCol != null && fkCol != null)
                        {
                            pkTable.addManyToManyKey(fkCol, pkCol);
                        }
                    }
                }

                pResultSet.close();
            }
        }
    }
    
	/**
	 * For each table, load the indexes.
	 */
    /*
	private void loadIndexes() throws SQLException
	{
		System.out.println("Database::loadIndexes");        
		Table tables[] = getTables();

		for(int i = 0; i < tables.length; i++)            
		{            
			Table table = tables[i];            
			ResultSet pResultSet =  meta.getIndexInfo(catalog, 
													  schema, 
													  table.getName(),
													  true,
													  true);
			while(pResultSet.next())
			{                
				String colName = pResultSet.getString("COLUMN_NAME");
				String indName = pResultSet.getString("INDEX_NAME");
				System.out.println("Found index "+indName+" on " + colName + " for table " +  table.getName());                
				Column col = table.getColumn(colName);
			}

			pResultSet.close();
		}
	}
	*/
}

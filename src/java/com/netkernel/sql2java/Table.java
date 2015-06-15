//$Id: Table.java,v 1.1 2005-10-20 19:43:22 fbr Exp $

package com.netkernel.sql2java;

import java.util.*;

public class Table
{

    private Map<String, Column> colHash = new Hashtable<String, Column>();
    private Vector<Column> cols = new Vector<Column>();
    private Vector<Column> priKey = new Vector<Column>();
    private Vector<Column> impKey = new Vector<Column>();
    private Hashtable<Column, Column> manyToManyMap = new Hashtable<Column, Column>();
    private String catalog, schema, name, type, remarks;

    public boolean isRelationTable()
    {
        return impKey.size() > 1;
    }

    /**
     * Tells whether if one of this table's columns (imported key)
     * points to one of the otherTable's pk.
     */
    public boolean relationConnectsTo(Table otherTable)
    {
        if (this.equals(otherTable))
        {
            return false;
        }

        for (int i = 0; i < impKey.size(); i++)
        {
            Column c = impKey.get(i);
            if (c.getTableName().equals(otherTable.getName()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Return, beside the passed table, the tables this table points to.
     */
    public Table[] linkedTables(Database pDatabase, Table pTable)
    {
        Vector<Table> pVector = new Vector<Table>();

        for (int iIndex = 0; iIndex < impKey.size(); iIndex++)
        {
            Column pColumn = impKey.get(iIndex);
            if (pColumn.getTableName().equals(pTable.getName()) == false)
            {
                Table pTableToAdd = pDatabase.getTable(pColumn.getTableName());
                if (pVector.contains(pTableToAdd) == false)
                    pVector.add(pTableToAdd);
            }
        }
        Table pReturn[] = new Table[pVector.size()];
        pVector.copyInto(pReturn);
        return pReturn;
    }

    /**
     * Return the imported key pointing to the passed table.
     */
    public Column getForeignKeyFor(Table pTable)
    {
        for (int iIndex = 0; iIndex < impKey.size(); iIndex++)
        {
            Column pColumn = impKey.get(iIndex);
            if (pColumn.getTableName().equals(pTable.getName()))
                return pColumn;
        }
        return null;
    }

    public void setCatalog(String catalog)
    {
        this.catalog = catalog;
    }
    public void setSchema(String schema)
    {
        this.schema = schema;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public void setType(String type)
    {
        this.type = type;
    }
    public void setRemarks(String remarks)
    {
        this.remarks = remarks;
    }

    public String getCatalog()
    {
        return catalog;
    }
    public String getSchema()
    {
        return schema;
    }
    public String getName()
    {
        return name;
    }
    public String getType()
    {
        return type;
    }
    public String getRemarks()
    {
        return remarks;
    }

    public Column[] getColumns()
    {
        Column list[] = new Column[cols.size()];
        cols.copyInto(list);
        return list;
    }

    public Column getColumn(String name)
    {
        return colHash.get(name.toLowerCase());
    }

    public void addColumn(Column column)
    {
        colHash.put(column.getName().toLowerCase(), column);
        cols.addElement(column);
    }

    public void removeColumn(Column column)
    {
        cols.removeElement(column);
        colHash.remove(column.getName().toLowerCase());
    }

    public Column[] getPrimaryKeys()
    {
        Column list[] = new Column[priKey.size()];
        priKey.copyInto(list);
        return list;
    }

    public void addPrimaryKey(Column column)
    {
        priKey.addElement(column);
        column.isPrimaryKey(true);
    }

    public Column[] getImportedKeys()
    {
        Column list[] = new Column[impKey.size()];
        impKey.copyInto(list);
        return list;
    }

    public void addImportedKey(Column column)
    {
        impKey.addElement(column);
        Column myColumn = getColumn(column.getForeignKeyColName());
        myColumn.setPointsTo(column);
    }

    /**
     * Returns a 2-D array of the keys in this table that form a many
     * to many relationship.
     * <br>
     * The size of the first dimension is based on the number of 
     * unique tables that are being managed. The second dimension
     * is always 2 elements. The first element is the column in the
     * relationship table itself. The second column is the primary
     * key column in the target table.
     */
    public Column[][] getManyToManyKeys()
    {

        //         // it matters only if we have 2 entries.
        //         if (manyToManyHash.size()<=1) {
        //             return new Column[0][0];
        //         }

        Column list[][] = new Column[manyToManyMap.size()][2];
        int i = 0;
        for (Enumeration<Column> e = manyToManyMap.keys(); e.hasMoreElements(); i++)
        {
            Column fk = e.nextElement();
            Column pk = manyToManyMap.get(fk);
            list[i][0] = fk;
            list[i][1] = pk;
        }
        return list;
    }

    public void addManyToManyKey(Column fk, Column pk)
    {
        manyToManyMap.put(fk, pk);
    }
}

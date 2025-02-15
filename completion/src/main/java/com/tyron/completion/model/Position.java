package com.tyron.completion.model;

/**
 * Represents the position in the editor in 
 * terms of lines and columns
 */
public class Position {

    public static final Position NONE = new Position(-1, -1);
    
    public int line;
    
    public int column;
    
    public Position(int line, int column) {
        this.line = line;
        this.column = column;
    }
    
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Position)) {
            return false;
        }
        Position that = (Position) object;
        return (this.line == that.line && this.column == that.column);
    }

    @Override
    public int hashCode() {
        return line + column;
    }
}

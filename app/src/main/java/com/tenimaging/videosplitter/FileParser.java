/*
 * Copyright (c) 2016. 10 Imaging Inc.
 */
package com.tenimaging.videosplitter;

import java.io.File;

/**
 * Supporting class to parse File paths and names
 */
public class FileParser {

    /**
     * Used to get the base file name without extension or path
     * @param file input file we want to get base name of
     * @return String containing the base file name
     */
    static public String getBaseName(File file) {

        // remove path and extension information from file name
        String name = file.getName();
        int index = name.indexOf(".");
        if ( index > 0 ) {
            name = name.substring(0,index);
        }
        return name;
    }
}

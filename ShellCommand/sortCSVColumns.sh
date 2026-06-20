#!/bin/sh
originalfilename=$1
newfilename=$2
basedir=$3
columnlist=$4
cd "$basedir"

if [ -s "$originalfilename" ]; then
    if [ -z "$columnlist" ]; then
        echo No list passed in
        csvcut -n $originalfilename | cut -c6- > "$originalfilename"_columns.txt
        tr '\n' ',' < "$originalfilename"_columns.txt > "$originalfilename"_column_list.txt
        truncate -s -1 "$originalfilename"_column_list.txt
        col_list=`cat "$originalfilename"_column_list.txt`
    else
        col_list=$columnlist
    fi
    echo $col_list
    csvcut -c $col_list "$originalfilename" > "$newfilename"
else
    cp "$originalfilename" "$newfilename"
fi

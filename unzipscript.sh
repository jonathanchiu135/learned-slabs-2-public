#! /bin/bash

i=21
p="w"
a=".oracleGeneral.bin.zst"

while [ $i -le 50 ]; do
        VAR="${p}${i}${a}"
        echo $VAR
        zstd -d $VAR
        i=$(( i + 1 ))
done             
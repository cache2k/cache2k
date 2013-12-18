set terminal svg
set output 'target/benchmark-result/traceWeb07hitrate.svg'
set boxwidth 0.9 absolute
set style fill solid 1.00 border lt -1
set key outside right top vertical Right noreverse noenhanced autotitles nobox
set style histogram clustered gap 2 title  offset character 0, 0, 0
set datafile missing '-'
set style data histograms
set xtics border in scale 0,0 nomirror rotate by -45  offset character 0, 0, 0 autojustify
set xtics  norangelimit font "1"
set xtics   ()
set title 'Hitrates for Web07 trace'
set yrange [ 0.0 : 77.308 ] noreverse nowriteback
plot 'target/benchmark-result/traceWeb07hitrate.dat' using 2:xtic(1) ti col, '' u 3 ti col, '' u 4 ti col, '' u 5 ti col, '' u 6 ti col, '' u 7 ti col, '' u 8 ti col, '' u 9 ti col, '' u 10 ti col

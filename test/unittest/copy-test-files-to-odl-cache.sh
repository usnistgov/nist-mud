
mkdir -p ../../sdnmud-aggregator/karaf/target/assembly/etc/mudprofiles/
target=../../sdnmud-aggregator/karaf/target/assembly/etc/mudprofiles
cp mud-local-and-same-mfg-test/mudfile-local-and-same-mfg-test.json  $target
cp mud-same-manufacturer-test/mudfile-same-manufacturer-test.json  $target
cp mud-mfg-test/mudfile-mfg-test.json  $target

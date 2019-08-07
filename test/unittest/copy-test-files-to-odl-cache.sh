
target=../../sdnmud-aggregator/karaf/target/assembly/etc/mudprofiles
mkdir -p $target
rm -f $target/*
cp mud-local-and-same-mfg-test/mudfile-local-and-same-mfg-test.json  $target
cp mud-same-manufacturer-test/mudfile-same-manufacturer-test.json  $target
cp mud-mfg-test/mudfile-mfg-test.json  $target
cp mud-local-networks-test/mudfile-local-networks-test.json $target
cp mud-tcp-direction-test/mudfile-tcp-direction-test.json $target
cp mud-tcp-direction-test-reporter/mudfile-tcp-direction-test-reporter.json $target
cp mud-model-test/mudfile-model-test.json $target
cp mud-same-mfg-scale-test/mudfile-same-mfg-scale-test.json $target
cp mud-mycontrollerclass-test/mudfile-mycontrollerclass-test.json $target
cp mud-controllerclass-test/mudfile-controllerclass-test.json $target
cp mud-dns-test/mudfile-dns-test.json $target
cp mud-quarantine-test/mudfile-quarantine-test.json $target

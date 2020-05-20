#!/bin/bash
echo " RUN THIS FROM THE EMULATION HOST "
echo "************** mud-logging-test ************"
cd mud-logging-test;sudo mn -c;sudo -E python mud-test.py; cd ../
exit(0)
echo "************** mud-local-and-same-mfg-test ************"
cd mud-local-and-same-mfg-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-local-and-same-mfg-test1 ************"
cd mud-local-and-same-mfg-test1;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-local-networks-test ************"
cd mud-local-networks-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-mfg-test ************"
cd mud-mfg-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-model-test ************"
cd mud-model-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-same-manufactuerer-test ************"
cd mud-same-manufacturer-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-controllerclass-test ************"
cd mud-controllerclass-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "************** mud-tcp-direction-test ************"
cd mud-tcp-direction-test;sudo mn -c;sudo -E python mud-test.py; cd ../
echo "DONE"


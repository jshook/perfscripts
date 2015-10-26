if [ -x 'bin/run-fio-tests' ]
then
 export FIO_TESTDIR=`pwd`
 export PATH=$PATH:${FIO_TESTDIR}/bin
 export FIO_RESULTS=${FIO_TESTDIR}/results
 export FIO_CONFDIR=${FIO_TESTDIR}/conf
 export FIO_DATADIR=change_me
else 
 echo "you must be in the fiotest directory to source this file."
fi


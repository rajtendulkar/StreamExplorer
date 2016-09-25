#!/bin/bash

# Enable Verbose mode.
#set -x

# Platform
# tilera or kalray
PLATFORM="kalray"

# Timeout in seconds for every query
PER_QUERY_TIMEOUT_IN_SECONDS=10

# Global timeout in seconds for total exploration
TIMEOUT_IN_SECONDS=180

# Root directory where StreamExplorer is located
HOME_DIR="/home/rajtendulkar/eclipse-workspace/Java-WorkSpace/spdf_with_Z3_integrated"

# Machine on which StreamExplorer is going to run is 32-bit or 64-bit?
ARCH="64-bit"

# Binary executables directory
BIN_DIR="$HOME_DIR/bin"

# Name of the experiment to perform
# Can be any of following values : 
##################################################################################################
# TILERA TILE-64 Platform
##################################################################################################
# latProcExploration	 : latency vs processors exploration
# latProcBuffExploration : latency vs processors vs buffer size exploration
# periodProcExploration  : period vs processors exploration
##################################################################################################
# Kalray MPPA-256 Platform
##################################################################################################
# designFlowNonPipelined : design flow with non-pipelined scheduling 
# designFlowPipelined    : design flow with pipelined scheduling
##################################################################################################
EXPT_NAME="designFlowNonPipelined"

# Directory to generate output files Stream Explorer
EXPLORATION_OUTPUT_DIR="outputFiles/${EXPT_NAME}/exploration"

# Directory to store the results of the schedule execution on hardware platform
HW_EXEC_OUTPUT_DIR="outputFiles/${EXPT_NAME}/hw_output"

# Remote directory where run-time is stored
REMOTE_WORKING_DIR="/home/rajtendulkar/runtime"

# Convert PLATFORM variable to lower case for comparison.
typeset -l $PLATFORM

if [[ $PLATFORM == "kalray" ]]; then
	
	# Name of the remote platform
	REMOTE_MACHINE="kalray-eth0"
	
	# Platform model file
	PLATFORM_XML_FILE="inputFiles/hardware_platform/kalray_mesh.xml"

	# Directory to store the application profile results from hardware platform
	PROFILE_OUTPUT_DIR="outputFiles/profile/kalray"

elif [[ $PLATFORM == "tilera" ]]; then
	
	# Name of the remote platform
	REMOTE_MACHINE="tilera-eth0"
	
	# Platform model file
	PLATFORM_XML_FILE="inputFiles/hardware_platform/tilera.xml"
	
	# Directory to store the application profile results from hardware platform
	PROFILE_OUTPUT_DIR="outputFiles/profile/tilera"

else
	REMOTE_MACHINE=""
	PLATFORM_XML_FILE=""
	PROFILE_OUTPUT_DIR=""
fi


# Application-Name      Enable/Disable      Run-Profile     Run Experiment		Execute App on Hardware  Profile XML file on hardware platform			local outputDir
tests=( 
"JpegDecoder"            "1"                "1"                 "1"                     "1"             "inputFiles/JpegDecoder/profile.xml"            "JpegDecoder"
"InsertionSort"          "1"                "0"                 "0"                     "0"             "inputFiles/InsertionSort/profile.xml"          "InsertionSort"
"MergeSort"              "1"                "0"                 "0"                     "0"             "inputFiles/MergeSort/profile.xml"              "MergeSort"
"RadixSort"              "1"                "0"                 "0"                     "0"             "inputFiles/RadixSort/profile.xml"              "RadixSort"
"Fft"                    "1"                "0"                 "0"                     "0"             "inputFiles/Fft/profile.xml"                    "Fft"
"MatrixMult"             "1"                "0"                 "0"                     "0"             "inputFiles/MatrixMult/profile.xml"             "MatrixMult"
"ComparisonCounting"     "1"                "0"                 "0"                     "0"             "inputFiles/ComparisonCounting/profile.xml"     "ComparisonCounting"
"BeamFormer"             "1"                "0"                 "0"                     "0"             "inputFiles/BeamFormer/profile.xml"             "BeamFormer"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete1.xml"          "Dct1"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete2.xml"          "Dct2"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete3.xml"          "Dct3"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete4.xml"          "Dct4"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete5.xml"          "Dct5"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete6.xml"          "Dct6"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete7.xml"          "Dct7"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/complete8.xml"          "Dct8"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/dct2DCoarse.xml"        "Dct9"
"Dct"                    "1"                "0"                 "0"                     "0"             "inputFiles/Dct/profile/dct2DFine.xml"          "Dct10"
)

#############################################################################################################
# *** From this point onwards you need not modify the script file unless you want to add new experiments ***
#############################################################################################################

if [ -z "$ARCH" ]
then
    MACHINE=`uname -m`
    if [ "$MACHINE" = "i686" ]
    then
        ARCH="32-bit"
    elif [ "$MACHINE" = "x86_64" ]
    then
        ARCH="64-bit"
    else
        echo "Unable to determine if Kernel is 32-bit or 64-bit"
        echo "Set ARCH variable manually in the script (32-bit / 64-bit)"
        exit
    fi
fi

# Where the Z3 jar file is located
JAR_DIR="$HOME_DIR/dep/Z3Lib/$ARCH"

# Common flags
JAVA_FLAGS="-Djava.library.path=$JAR_DIR -classpath $JAR_DIR/com.microsoft.z3.jar:$BIN_DIR:$JAR_DIR"

# Compile the Java source code.
javac -sourcepath src/ -d bin/ $(find src/ -name *.java) -classpath $JAR_DIR/com.microsoft.z3.jar

#########################################################################################################################################################
# Different StreamExplorer experiments
#########################################################################################################################################################

# Function to profile application on the hardware platform.
# Arguments - 
#1 - application name.
#2 - Profile XML file
#3 - output directory name
function profileAppOnPlatform {
    local APPLICATION=$1
    local PROFILE_XML=$2
    local CURRENT_OUTPUT_DIR=$3

	echo "Profiling $APPLICATION on the platform"

    ssh $REMOTE_MACHINE "cd $REMOTE_WORKING_DIR ; make all run-hw APPLICATION=$APPLICATION APPLICATION_ARGS="$PROFILE_XML" ; exit"
    mkdir -p "$CURRENT_OUTPUT_DIR"
    scp $REMOTE_MACHINE:${REMOTE_WORKING_DIR}/defaultProfile.xml ${CURRENT_OUTPUT_DIR}/

}

# From Profile Info, generate a Application Graph XML
# 1- input XML file
# 2- output XML file
function parseProfileInfo {
    # Generate the XML File which we should use for the experiments 
    java $JAVA_FLAGS experiments.others.GenerateAppXmlFromProfile -px $1 -ox $2
}

# Execute the solution of design space exploration on the hardware
# Arguments - 
#1 - application name.
#2 - application output directory name
#3 - schedule XML
function executeSolutionsOnHardware {
    local APPLICATION=$1
    local APP_OUTPUT_DIR=$2
	local SCHEDULE_XML=$3
    
    scp $SCHEDULE_XML ${REMOTE_MACHINE}:${REMOTE_WORKING_DIR}/schedule.xml
    ssh $REMOTE_MACHINE "cd $REMOTE_WORKING_DIR ; make all run-hw APPLICATION=$APPLICATION APPLICATION_ARGS="schedule.xml" | tee execution.txt ; rm -f schedule.xml ; exit"
    scp $REMOTE_MACHINE:${REMOTE_WORKING_DIR}/rawData.txt $APP_OUTPUT_DIR/rawData.txt
    scp $REMOTE_MACHINE:${REMOTE_WORKING_DIR}/defaultProfile.xml $APP_OUTPUT_DIR/profile.xml
    scp $REMOTE_MACHINE:${REMOTE_WORKING_DIR}/execution.txt $APP_OUTPUT_DIR/execution.txt
    ssh $REMOTE_MACHINE "cd $REMOTE_WORKING_DIR ; rm -f defaultProfile.xml rawData.txt execution.txt ; exit"
}

# Latency vs Processors Exploration
# Arguments - 
#1 - Output Directory
#2 - Application XML file
function latProcExploration {
	local CURRENT_OUTPUT_DIR=$1
	local APP_XML=$2
	local CONFIG_FLAGS="-localtimeout $PER_QUERY_TIMEOUT_IN_SECONDS -globaltimeout $TIMEOUT_IN_SECONDS -od ${CURRENT_OUTPUT_DIR}"
	CONFIG_FLAGS="$CONFIG_FLAGS -solver mutualExclusion -psym True -gsym True -proc 62 -ag ${APP_XML}"

    java $JAVA_FLAGS experiments.sharedMemory.twoDimension.LatProcExploration ${CONFIG_FLAGS} | tee solverExecution.log
	mv solverExecution.log  ${CURRENT_OUTPUT_DIR}/
}

# Latency vs Processors vs Buffer Size Exploration
# Arguments - 
#1 - Output Directory
#2 - Application XML file
function latProcBuffExploration {
	local CURRENT_OUTPUT_DIR=$1
	local APP_XML=$2
	local CONFIG_FLAGS="-localtimeout $PER_QUERY_TIMEOUT_IN_SECONDS -globaltimeout $TIMEOUT_IN_SECONDS -od ${CURRENT_OUTPUT_DIR} -buffer True"
	CONFIG_FLAGS="$CONFIG_FLAGS -solver mutualExclusion -psym True -gsym True -proc 62 -ag ${APP_XML}"

    java $JAVA_FLAGS experiments.sharedMemory.threeDimension.LatProcBuffExploration ${CONFIG_FLAGS} | tee solverExecution.log
	mv solverExecution.log  ${CURRENT_OUTPUT_DIR}/
}


# Period vs Processors Exploration
# Arguments - 
#1 - Output Directory
#2 - Application XML file
function periodProcExploration {
	local CURRENT_OUTPUT_DIR=$1
	local APP_XML=$2
	local CONFIG_FLAGS="-localtimeout $PER_QUERY_TIMEOUT_IN_SECONDS -globaltimeout $TIMEOUT_IN_SECONDS -od ${CURRENT_OUTPUT_DIR} -buffer False"
	CONFIG_FLAGS="$CONFIG_FLAGS -psym True -gsym True -proc 62 -ag ${APP_XML} -solver periodLocality -disablePrime True"

    java $JAVA_FLAGS experiments.sharedMemory.twoDimension.PeriodProcExploration ${CONFIG_FLAGS} | tee solverExecution.log
	mv solverExecution.log  ${CURRENT_OUTPUT_DIR}/
}

# Period vs Processors Exploration
# Arguments - 
#1 - Output Directory
#2 - Application XML file
#3 - Platform XML file
function designFlowNonPipelined {
	echo Non Pipelined Design Flow Experiment
	local CURRENT_OUTPUT_DIR=$1
	local APP_XML=$2
	local PLATFORM_XML=$3
	local CONFIG_FLAGS="-localtimeout $PER_QUERY_TIMEOUT_IN_SECONDS -globaltimeout $TIMEOUT_IN_SECONDS -od ${CURRENT_OUTPUT_DIR}"
	CONFIG_FLAGS="$CONFIG_FLAGS -psym True -gsym True -ag ${APP_XML} -pg ${PLATFORM_XML}"
	java $JAVA_FLAGS experiments.distributedMemory.DesignFlowNonPipelined ${CONFIG_FLAGS} | tee solverExecution.log 
	mv solverExecution.log  ${CURRENT_OUTPUT_DIR}/
}

function designFlowPipelined {
	echo Pipelined Experiment Design Flow Experiment
}

#########################################################################################################################################################

# Run the experiments
for (( i = 0; i < ${#tests[*]} ; i+=7 ))
do
	# Check if the application benchmark is enabled or not
    if [ "${tests[$i+1]}" == "1" ]; then

		APPLICATION_NAME=${tests[$i]}
		PROFILE_XML=${tests[$i+5]}
		PROFILE_DIR=$PROFILE_OUTPUT_DIR/${tests[$i+6]}
		EXPLORATION_DIR=$EXPLORATION_OUTPUT_DIR/$APPLICATION_NAME
		HW_DIR=$HW_EXEC_OUTPUT_DIR/$APPLICATION_NAME

        # Check if we have to run profiling.
        if [ "${tests[$i+2]}" == "1" ]; then

        	mkdir -p $PROFILE_DIR
            profileAppOnPlatform $APPLICATION_NAME ${PROFILE_XML} "${PROFILE_DIR}" | tee profile_log.txt
            mv profile_log.txt ${PROFILE_DIR}/

			# Convert the hardware profile info	to Application XML
			parseProfileInfo ${PROFILE_DIR}/defaultProfile.xml ${PROFILE_DIR}/${APPLICATION_NAME}.xml
        fi

		# Execute different experiments from here, if they are enabled.
        if [ "${tests[$i+3]}" == "1" ]; then

			########################################################
			# Note : Enable only one of the experiments. some of 
			# them have common output directory names.
			########################################################

			########################################################
			# Experiments particularly for Tilera TILE-64 platform
			########################################################
			# latProcExploration
			# latProcBuffExploration
			# periodProcExploration
			########################################################
			
			########################################################
			# Experiments particularly for Kalray MPPA-256 platform
			########################################################
			# designFlowNonPipelined
			# designFlowPipelined
			########################################################

			if [ "${EXPT_NAME}" == "latProcExploration" ]; then
				# Latency vs Processor Exploration
				latProcExploration ${EXPLORATION_DIR} ${PROFILE_DIR}/${APPLICATION_NAME}.xml
			elif  [ "${EXPT_NAME}" == "latProcBuffExploration" ]; then
				# Latency vs Processor vs Buffer-size Exploration
				latProcBuffExploration ${EXPLORATION_DIR} ${PROFILE_DIR}/${APPLICATION_NAME}.xml
			elif  [ "${EXPT_NAME}" == "periodProcExploration" ]; then
				# Period vs Processor Exploration
				periodProcExploration ${EXPLORATION_DIR} ${PROFILE_DIR}/${APPLICATION_NAME}.xml
			elif  [ "${EXPT_NAME}" == "designFlowNonPipelined" ]; then
				# DesignFlow Non Pipelined Scheduling
				designFlowNonPipelined ${EXPLORATION_DIR} ${PROFILE_DIR}/${APPLICATION_NAME}.xml ${PLATFORM_XML_FILE}
			elif  [ "${EXPT_NAME}" == "designFlowPipelined" ]; then
				# DesignFlow Pipelined Scheduling
				designFlowPipelined
			else
				echo "********* UNKNOWN EXPERIMENT : ${EXPT_NAME} *********"
				exit 0
			fi
			########################################################
        fi

        # Check if we have to execute the app on the hardware.
        if [ "${tests[$i+4]}" == "1" ]; then
			XML_FILES=`find ${EXPLORATION_DIR} -name "*.xml"`

			for xmlfile in $XML_FILES ; do
				SOL_DIR=$HW_EXEC_OUTPUT_DIR/$APPLICATION_NAME/`echo ${xmlfile} | awk -F "${APPLICATION_NAME}/" '{print $2}' | cut -f 1 -d "." | rev | cut -d "/" -f 1 --complement | rev` 
				mkdir -p ${SOL_DIR}
				executeSolutionsOnHardware ${APPLICATION_NAME} ${SOL_DIR} $xmlfile | tee execution_log.txt
            	mv execution_log.txt ${SOL_DIR}/
			done
        fi
    fi
done


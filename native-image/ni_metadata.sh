#!/bin/bash

# Collects conditional metadata necessary for native-image by running the netty test suites.
# Since GraalVM 22.1, the native-image-agent has support for automatically generating conditional metadata.
# Unlike the standard metadata, conditional metadata is only included if the condition evaluates to true during an image build.
# At the time of writing, the only condition supported by native-image is `whenTypeReachable`: a metadata entry is only included if the condition type is reachable during the image build.
#
# This script executes the netty build and attaches the native-image-agent to the test execution.
# Once all of the tests are executed, the metadata generated accross all of the runs is merged and placed into the `common` module.
# As the metadata is conditional, it should not impact the footprint of native-images that do not use all of netty's functionality.

SCRIPT_PATH="$(dirname $(realpath -s $0))"
# This filter tells the native-image-agent which classes belong to netty. Only these classes will be used as conditions for the generated metadata.
USER_CODE_FILTER="${SCRIPT_PATH}/netty-filter.json"
# This filter tells the native-image-agent which classes to filter out from conditions and metadata entries themselves.
# We do not need for e.g. Mockito mock classes and Test classes in the resulting metadata.
EXTRA_FILTER="${SCRIPT_PATH}/test-class-filter.json"

# Prepare the output directories.
PROJECT_ROOT="${SCRIPT_PATH}/.."
OUTPUT_ROOT="${PROJECT_ROOT}/target/native-config"
COLLECTED_CONFIG_PATH="${OUTPUT_ROOT}/collected"
MERGED_CONFIG_PATH="${PROJECT_ROOT}/common/src/main/resources/META-INF/native-image/generated"

# Clear the previous configuration.
rm -rf "${OUTPUT_ROOT}" "${MERGED_CONFIG_PATH}"
mkdir -p "${COLLECTED_CONFIG_PATH}" "${MERGED_CONFIG_PATH}"

# Execute the netty build.
cd "${SCRIPT_PATH}/.."
mvn -DfailIfNoTests=false -Dtest=\!EpollDatagramMulticastIpv6WithIpv4AddrTest,\!ShadingIT,\!FlowControlHandlerTest,\!CloseNotifyTest,\!Http2MultiplexTransportTest -Dit.test=\!ShadingIT -DskipHttp2Testsuite=true -DargLine.javaProperties="-Dio.netty.tryReflectionSetAccessible=true -agentlib:native-image-agent=config-output-dir=${COLLECTED_CONFIG_PATH}/{pid},experimental-conditional-config-part" package

# Merge all of the generated metadata.
NI_CONFIG_INPUT_ARGS=""
for i in $(ls $COLLECTED_CONFIG_PATH); do
    NI_CONFIG_INPUT_ARGS="${NI_CONFIG_INPUT_ARGS} --input-dir=${COLLECTED_CONFIG_PATH}/${i}"
done
echo $NI_CONFIG_INPUT_ARGS

native-image-configure generate-conditional --user-code-filter=${USER_CODE_FILTER} --class-name-filter=${EXTRA_FILTER} ${NI_CONFIG_INPUT_ARGS} --output-dir=${MERGED_CONFIG_PATH}

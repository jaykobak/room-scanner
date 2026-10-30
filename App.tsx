import React, {useEffect, useState} from 'react';
import {
  requireNativeComponent,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  Text,
  StyleSheet,
  View,
} from 'react-native';

type ArCoreViewProps = {
  style?: object;
  onDepthStatus?: (event: {
    nativeEvent: {status: string};
  }) => void;
};

const ArCoreView =
  requireNativeComponent<ArCoreViewProps>('ArCoreView');

export default function App() {
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [depthStatus, setDepthStatus] = useState('Waiting for camera permission');

  useEffect(() => {
    const requestPermission = async () => {
      try {
        const result = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
        );

        if (result === PermissionsAndroid.RESULTS.GRANTED) {
          setPermissionGranted(true);
          setDepthStatus('Starting scanner...');
        } else {
          setDepthStatus('Camera permission denied');
        }
      } catch (error) {
        setDepthStatus(`Permission error: ${String(error)}`);
      }
    };

    requestPermission();
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Room Scanner</Text>

      {permissionGranted ? (
        <ArCoreView
          style={styles.camera}
          onDepthStatus={event =>
            setDepthStatus(event.nativeEvent.status)
          }
        />
      ) : null}

      <View style={styles.statusContainer}>
        <Text style={styles.statusTitle}>Depth status</Text>
        <Text style={styles.status}>{depthStatus}</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  title: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginVertical: 20,
  },
  camera: {
    flex: 1,
    width: '100%',
  },
  statusContainer: {
    padding: 20,
    backgroundColor: '#1f2937',
  },
  statusTitle: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  status: {
    color: '#93c5fd',
    marginTop: 8,
    fontSize: 14,
  },
});
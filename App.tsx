import React, {useEffect, useState} from 'react';
import {
  requireNativeComponent,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  Text,
  StyleSheet,
} from 'react-native';

const ArCoreView = requireNativeComponent('ArCoreView');

export default function App() {
  const [permissionGranted, setPermissionGranted] = useState(false);

  useEffect(() => {
    const requestPermission = async () => {
      if (Platform.OS === 'android') {
        const result = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
        );

        setPermissionGranted(
          result === PermissionsAndroid.RESULTS.GRANTED,
        );
      }
    };

    requestPermission();
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Room Scanner</Text>

      {permissionGranted ? (
        <ArCoreView style={styles.camera} />
      ) : (
        <Text style={styles.info}>Waiting for camera permission...</Text>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    alignItems: 'center',
  },
  title: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    marginVertical: 20,
  },
  camera: {
    flex: 1,
    width: '100%',
  },
  info: {
    color: '#fff',
    padding: 16,
  },
});
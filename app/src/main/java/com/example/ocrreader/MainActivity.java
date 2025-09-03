package com.example.ocrreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.datatransport.runtime.BuildConfig;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    ImageView imgView;



    private Bitmap ultimaImagemProcessada = null;

    private String txtExtraido = "";

    private TextView extractView;

    private TextRecognizer textRecognizer;
    //private Uri fotoUri;
    private final ActivityResultLauncher<String> launcherGaleria = registerForActivityResult(
            new ActivityResultContracts.GetContent(),this::processarImagemSelecionada
    );

    private final ActivityResultLauncher<Intent> launcherCamerea = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),this::processarFotoTirada
    );

    private Uri fotoUri;
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_READ_IMAGES = 1002;
    private static final int REQ_WRITE_STORAGE = 1003;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

         FloatingActionButton btnCamera = findViewById(R.id.captureButton);
         FloatingActionButton btnGalery = findViewById(R.id.captureGalery);
         imgView = findViewById(R.id.imageView);
         extractView = findViewById(R.id.extractedTextView);

        btnCamera.setOnClickListener(v -> {
            checarOuAbrirCamera();
        });

        btnGalery.setOnClickListener(v -> {
            abrirGaleria();
        });

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);


    }

    private void checarOuAbrirCamera(){
        if(ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);

        }
        else {
            abrirCamera();
        }
    }

    private void abrirCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Criar um arquivo temporário para salvar a imagem
        File photoFile = criarArquivoImagem();
        if (photoFile != null) {
            fotoUri = FileProvider.getUriForFile(this,
                    "com.example.ocrreader.fileprovider",
                    photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fotoUri);

            try {
                launcherCamerea.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir a Câmera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Não foi possível criar o arquivo para a imagem", Toast.LENGTH_SHORT).show();
        }
    }

    private File criarArquivoImagem() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    private void abrirGaleria() {
//        if(Build.VERSION.SDK_INT>=33){
//
//        }
        launcherGaleria.launch("image/*");
    }

    private void processarFotoTirada(ActivityResult activityResult) {
        if (activityResult.getResultCode() != RESULT_OK || fotoUri == null) {
            Toast.makeText(this, "Operação cancelada", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Obter o Bitmap a partir do Uri
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fotoUri);

            // Aplicar rotação com base nos metadados EXIF
            int rotation = getRotationFromExif(fotoUri);
            ultimaImagemProcessada = applyRotation(bitmap, rotation);

            // Exibir a imagem corrigida
            imgView.setImageBitmap(ultimaImagemProcessada);

            // Extrair texto da imagem
            extrairTexto(ultimaImagemProcessada);

        } catch (Exception e) {
            Toast.makeText(this, "Erro ao processar a imagem: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    private int getRotationFromExif(@NonNull Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {

                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) {
            return 0; // Se houver erro, assume que não há rotação.
        }
    }

    private Bitmap applyRotation(Bitmap src, int degrees) {
        if (degrees == 0 || src == null) return src;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }



    private void processarImagemSelecionada(Uri uri) {
        if(uri == null) return;

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

            //rotularBitmap(bitmap);

            // Lê os metadados EXIF da imagem para descobrir sua orientação (se foi tirada de lado, etc.).
            int rotation = getRotationFromExif(uri);
            // Aplica a rotação para que a imagem fique "em pé".
            ultimaImagemProcessada = applyRotation(bitmap, rotation);
           // ultimaImagemProcessada = bitmap; // ou bitmap, no caso da galeriae
            imgView.setImageBitmap(ultimaImagemProcessada);

            extrairTexto(ultimaImagemProcessada);





        }catch (Exception e){
            Toast.makeText(this, "Não foi possível abrir a imagem "+ e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    private void extrairTexto(Bitmap bitmap){
        if (ultimaImagemProcessada == null){
            mostrarToast("Nenhuma Imagem selecionada");
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        textRecognizer.process(image).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text text) {
                txtExtraido = text.getText();
                extractView.setText(txtExtraido);

            }


        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                mostrarToast("Falha ao extrair Texto"+ e.getMessage());
            }
        });
    }

    private void mostrarToast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }
}
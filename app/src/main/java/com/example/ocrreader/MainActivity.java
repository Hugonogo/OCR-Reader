package com.example.ocrreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class MainActivity extends AppCompatActivity {

    ImageView imgView;

    private Bitmap ultimaImagemProcessada = null;
    private String txtExtraido = "";

    private TextView extractView;

    private TextRecognizer textRecognizer;

    private final ActivityResultLauncher<String> launcherGaleria = registerForActivityResult(
            new ActivityResultContracts.GetContent(),this::processarImagemSelecionada
    );

    private final ActivityResultLauncher<Intent> launcherCamerea = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),this::processarFotoTirada
    );
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

        try {
            launcherCamerea.launch(intent);

        }catch (Exception e){
            Toast.makeText(this, "Não foi possivel abrir a Câmera "+ e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirGaleria() {
//        if(Build.VERSION.SDK_INT>=33){
//
//        }
        launcherGaleria.launch("image/*");
    }

    private void processarFotoTirada(ActivityResult activityResult) {
        if (activityResult == null || activityResult.getData() == null){
            Toast.makeText(this, "Operação cancelada", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap thumbnail = (Bitmap) activityResult.getData().getExtras().get("data");

        if(thumbnail == null){
            Toast.makeText(this, "Câmera não retornou a imagem", Toast.LENGTH_SHORT).show();
            return;
        }

        ultimaImagemProcessada = thumbnail; // ou bitmap, no caso da galeria

        imgView.setImageBitmap(thumbnail);

        extrairTexto(ultimaImagemProcessada);
    }

    private void processarImagemSelecionada(Uri uri) {
        if(uri == null) return;

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            imgView.setImageBitmap(bitmap);
            //rotularBitmap(bitmap);

            ultimaImagemProcessada = bitmap; // ou bitmap, no caso da galeriae
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
package com.example.ocrreader;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    ImageView imgView; // Exibe a imagem capturada ou escolhida
    private Bitmap ultimaImagemProcessada = null; // Última imagem usada
    private String txtExtraido = ""; // Texto OCR extraído
    private TextView extractView; // Exibe o texto OCR

    private TextRecognizer textRecognizer; // Cliente ML Kit OCR

    // Launcher para abrir galeria
    private final ActivityResultLauncher<String> launcherGaleria = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::processarImagemSelecionada
    );

    // Launcher para abrir câmera
    private final ActivityResultLauncher<Intent> launcherCamerea = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::processarFotoTirada
    );

    // Launcher para salvar arquivo
    private final ActivityResultLauncher<Intent> launcherSalvar =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            // Salva conteúdo no arquivo escolhido
                            OutputStream outputStream = getContentResolver().openOutputStream(uri);
                            if (outputStream != null) {
                                outputStream.write(extractView.getText().toString().getBytes());
                                outputStream.close();
                                mostrarToast("Arquivo salvo com sucesso!");
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            mostrarToast("Erro ao salvar: " + e.getMessage());
                        }
                    }
                }
            });

    private Uri fotoUri; // Caminho da foto tirada
    private static final int REQ_CAMERA = 1001; // Código de permissão da câmera
    private static final int REQ_READ_IMAGES = 1002; // Código de permissão para ler imagens
    private static final int REQ_WRITE_STORAGE = 1003; // Código de permissão para salvar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ajusta margens para não sobrepor status/navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Botões
        FloatingActionButton btnCamera = findViewById(R.id.captureButton);
        FloatingActionButton btnGalery = findViewById(R.id.captureGalery);
        FloatingActionButton btnTranslate = findViewById(R.id.translateButton);
        imgView = findViewById(R.id.imageView);
        extractView = findViewById(R.id.extractedTextView);
        MaterialButton btnShare = findViewById(R.id.shareButton);
        MaterialButton btnCopy = findViewById(R.id.copyButton);
        MaterialButton btnSave = findViewById(R.id.saveButton);

        // Ações dos botões
        btnCamera.setOnClickListener(v -> checarOuAbrirCamera()); // Abre câmera
        btnGalery.setOnClickListener(v -> abrirGaleria()); // Abre galeria
        btnShare.setOnClickListener(v -> compartilharResultados()); // Compartilha
        btnCopy.setOnClickListener(v -> copiarTexto(extractView.getText().toString())); // Copia texto
        btnSave.setOnClickListener(v -> escolherPastaESalvar(extractView.getText().toString())); // Salva
        btnTranslate.setOnClickListener(v -> translateTxt(extractView.getText().toString())); // Traduz

        // Inicializa OCR
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    // Traduz texto usando ML Kit Translation
    private void translateTxt(String txt) {
        if (txt == null || txt.trim().isEmpty() || txt.equals("Texto extraído aparecerá aqui")) {
            mostrarToast("Nada para traduzir");
            return;
        }

        // Configuração: Inglês -> Português
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                .build();

        final Translator translator = Translation.getClient(options);

        // Baixa modelo se necessário
        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();

        translator.downloadModelIfNeeded(conditions).addOnSuccessListener(unused -> {
            // Traduz texto
            translator.translate(txt).addOnSuccessListener(traString -> {
                extractView.setText(traString);
                mostrarToast("Tradução Concluida");
            }).addOnFailureListener(e -> mostrarToast("Erro na tradução: " + e.getMessage()));
        }).addOnFailureListener(e -> mostrarToast("Erro ao baixar modelo"));
    }

    // Verifica permissão e abre câmera
    private void checarOuAbrirCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            abrirCamera();
        }
    }

    // Abre câmera
    private void abrirCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = criarArquivoImagem(); // Cria arquivo temporário
        if (photoFile != null) {
            fotoUri = FileProvider.getUriForFile(this, "com.example.ocrreader.fileprovider", photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fotoUri);
            try {
                launcherCamerea.launch(intent);
            } catch (Exception e) {
                mostrarToast("Não foi possível abrir a Câmera: " + e.getMessage());
            }
        } else {
            mostrarToast("Não foi possível criar o arquivo para a imagem");
        }
    }

    // Cria arquivo temporário para foto
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

    // Abre galeria
    private void abrirGaleria() {
        launcherGaleria.launch("image/*");
    }

    // Processa foto tirada com câmera
    private void processarFotoTirada(ActivityResult activityResult) {
        if (activityResult.getResultCode() != RESULT_OK || fotoUri == null) {
            mostrarToast("Operação cancelada");
            return;
        }

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fotoUri);
            int rotation = getRotationFromExif(fotoUri); // Corrige rotação
            ultimaImagemProcessada = applyRotation(bitmap, rotation);
            imgView.setImageBitmap(ultimaImagemProcessada); // Exibe
            extrairTexto(ultimaImagemProcessada); // OCR
        } catch (Exception e) {
            mostrarToast("Erro ao processar a imagem: " + e.getMessage());
        }
    }

    // Obtém rotação da imagem pelos metadados EXIF
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
            return 0;
        }
    }

    // Aplica rotação real no bitmap
    private Bitmap applyRotation(Bitmap src, int degrees) {
        if (degrees == 0 || src == null) return src;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    // Processa imagem da galeria
    private void processarImagemSelecionada(Uri uri) {
        if (uri == null) return;
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            int rotation = getRotationFromExif(uri);
            ultimaImagemProcessada = applyRotation(bitmap, rotation);
            imgView.setImageBitmap(ultimaImagemProcessada);
            extrairTexto(ultimaImagemProcessada);
        } catch (Exception e) {
            mostrarToast("Não foi possível abrir a imagem " + e.getMessage());
        }
    }

    // Extrai texto da imagem (OCR)
    private void extrairTexto(Bitmap bitmap) {
        if (ultimaImagemProcessada == null) {
            mostrarToast("Nenhuma imagem selecionada");
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
                mostrarToast("Falha ao extrair Texto " + e.getMessage());
            }
        });
    }

    // Exibe mensagem rápida
    private void mostrarToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // Compartilha imagem + texto
    private void compartilharResultados() {
        if (ultimaImagemProcessada == null || extractView.getText().toString().isEmpty()) {
            mostrarToast("Nenhum resultado para compartilhar");
            return;
        }
        try {
            String path = MediaStore.Images.Media.insertImage(
                    getContentResolver(), ultimaImagemProcessada, "resultado_rotulos", null
            );
            Uri uri = Uri.parse(path);
            String mensagem = "Resultados da análise:\n\n" + extractView.getText().toString();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_TEXT, mensagem);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Compartilhar Com:"));
        } catch (Exception e) {
            mostrarToast("Erro ao compartilhar: " + e.getMessage());
        }
    }

    // Copia texto para área de transferência
    private void copiarTexto(String textoCopiado) {
        if (textoCopiado == null || textoCopiado.trim().isEmpty() || textoCopiado.equals("Texto extraído aparecerá aqui")) {
            mostrarToast("Nada para Copiar");
            return;
        }
        ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipBoard.setPrimaryClip(ClipData.newPlainText("", textoCopiado));
        mostrarToast("Texto Copiado");
    }

    // Salva texto em arquivo
    private void escolherPastaESalvar(String conteudo) {
        if (conteudo == null || conteudo.trim().isEmpty() || conteudo.equals("Texto extraído aparecerá aqui")) {
            mostrarToast("Nada para salvar");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "arquivo.txt");
        launcherSalvar.launch(intent);
    }
}

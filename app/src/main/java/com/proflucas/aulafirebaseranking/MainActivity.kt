package com.proflucas.aulafirebaseranking

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.getValue

class MainActivity : ComponentActivity() {

    private var pontos = 0

    private val pontuacoesRef: DatabaseReference = FirebaseDatabase
        .getInstance()
        .getReference("pontuacoes")

    // Guarda a chave do Firebase junto com os dados de cada pontuação.
    private data class PontuacaoComChave(
        val chave: String,
        val pontuacao: Pontuacao
    )

    private var pontuacoesCarregadas = emptyList<PontuacaoComChave>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val campoNome = findViewById<EditText>(R.id.campoNome)
        val textoPontuacao = findViewById<TextView>(R.id.textoPontuacao)
        val textoRanking = findViewById<TextView>(R.id.textoRanking)
        val botaoAumentarPontos = findViewById<Button>(R.id.botaoAumentarPontos)
        val botaoDiminuirPontos = findViewById<Button>(R.id.botaoDiminuirPontos)
        val botaoSalvar = findViewById<Button>(R.id.botaoSalvar)

        botaoAumentarPontos.setOnClickListener {
            pontos++
            textoPontuacao.text = "Pontuação: $pontos"
        }

        botaoDiminuirPontos.setOnClickListener {
            if (pontos <= 0){
                pontos = 0
                Toast.makeText(this, "Jogador com 0 pontos!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pontos--
            textoPontuacao.text = "Pontuação: $pontos"
        }

        botaoSalvar.setOnClickListener {
            val nome = campoNome.text.toString().trim()

            if (nome.isEmpty()) {
                campoNome.error = "Digite seu nome"
            } else {
                salvarPontuacao(nome)
            }
        }

        // Tocar no ranking abre as opções de editar ou excluir.
        textoRanking.setOnClickListener {
            abrirSeletorDePontuacao()
        }

        carregarRanking(textoRanking)
    }

    private fun salvarPontuacao(nome: String) {
        val pontuacao = Pontuacao(nome, pontos)

        pontuacoesRef.push()
            .setValue(pontuacao)
            .addOnSuccessListener {
                Toast.makeText(this, "Pontuação salva.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { erro ->
                textoErro(erro.message)
            }
    }

    private fun carregarRanking(textoRanking: TextView) {
        pontuacoesRef
            .orderByChild("pontos")
            .limitToLast(10)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val lista = mutableListOf<PontuacaoComChave>()

                    for (item in snapshot.children) {
                        val chave = item.key
                        val pontuacao = item.getValue(Pontuacao::class.java)

                        if (chave != null && pontuacao != null) {
                            lista.add(
                                PontuacaoComChave(
                                    chave = chave,
                                    pontuacao = pontuacao
                                )
                            )
                        }
                    }

                    lista.reverse()
                    pontuacoesCarregadas = lista

                    val linhas = lista.mapIndexed { indice, item ->
                        "${indice + 1}. ${item.pontuacao.nome} - " +
                            "${item.pontuacao.pontos} pontos"
                    }

                    textoRanking.text = if (linhas.isEmpty()) {
                        "Ainda não há pontuações."
                    } else {
                        "Ranking (toque aqui para editar ou excluir):\n" +
                            linhas.joinToString("\n")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    textoRanking.text = "Erro ao carregar o ranking."
                    textoErro(error.message)
                }
            })
    }

    private fun abrirSeletorDePontuacao() {
        if (pontuacoesCarregadas.isEmpty()) {
            Toast.makeText(this, "Não há pontuações para editar.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val opcoes = pontuacoesCarregadas.map { item ->
            "${item.pontuacao.nome} - ${item.pontuacao.pontos} pontos"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha uma pontuação")
            .setItems(opcoes) { _, indice ->
                val selecionada = pontuacoesCarregadas[indice]
                abrirAcoesDaPontuacao(selecionada)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirAcoesDaPontuacao(item: PontuacaoComChave) {
        AlertDialog.Builder(this)
            .setTitle(item.pontuacao.nome)
            .setItems(arrayOf("Editar pontos", "Excluir pontuação")) { _, escolha ->
                when (escolha) {
                    0 -> abrirEdicao(item)
                    1 -> confirmarExclusao(item)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirEdicao(item: PontuacaoComChave) {
        val campoPontos = EditText(this).apply {
            hint = "Nova pontuação"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(item.pontuacao.pontos.toString())
        }

        AlertDialog.Builder(this)
            .setTitle("Editar pontuação")
            .setMessage("Digite a nova quantidade de pontos.")
            .setView(campoPontos)
            .setPositiveButton("Salvar") { _, _ ->
                val novoValor = campoPontos.text.toString().toIntOrNull()

                if (novoValor == null || novoValor < 0) {
                    Toast.makeText(
                        this,
                        "Digite um número igual ou maior que zero.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    editarPontuacao(item, novoValor)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editarPontuacao(item: PontuacaoComChave, novoValor: Int) {
        pontuacoesRef
            .child(item.chave)
            .child("pontos")
            .setValue(novoValor)
            .addOnSuccessListener {
                Toast.makeText(this, "Pontuação atualizada.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { erro ->
                textoErro(erro.message)
            }
    }

    private fun confirmarExclusao(item: PontuacaoComChave) {
        AlertDialog.Builder(this)
            .setTitle("Excluir pontuação")
            .setMessage(
                "Deseja excluir a pontuação de ${item.pontuacao.nome} " +
                    "(${item.pontuacao.pontos} pontos)?"
            )
            .setPositiveButton("Excluir") { _, _ ->
                excluirPontuacao(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluirPontuacao(item: PontuacaoComChave) {
        pontuacoesRef
            .child(item.chave)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Pontuação excluída.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { erro ->
                textoErro(erro.message)
            }
    }

    private fun textoErro(mensagem: String?) {
        android.util.Log.e("Firebase", mensagem ?: "Ocorreu um erro")
        Toast.makeText(
            this,
            "Erro: ${mensagem ?: "não foi possível concluir a operação"}",
            Toast.LENGTH_LONG
        ).show()
    }
}
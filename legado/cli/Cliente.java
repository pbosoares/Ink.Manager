	class Cliente {

private int id;
private String nome;
private String telefone;
private int idade;
private String instagram;
	

	public Cliente() {

	}

	public Cliente(String nome, String telefone, int idade, String instagram) {

	this.nome = nome;
	this.telefone = telefone;
	this.idade = idade;
	this.instagram = instagram;

}

 public String getNome() {
        return nome;
    }

  public void setNome(String nome) {
        this.nome = nome;
    }

  public String getTelefone() {
  		return telefone;
  }

  public void setTelefone(String telefone) {
  		this.telefone = telefone;
  }

  public int getIdade() {
  		return idade;
  }

  public void setIdade(int idade) {
  		this.idade = idade;
  }

  public String getInstagram() {
  		return instagram;
  }

  public void setInstagram(String instagram) {
  		this.instagram = instagram;
  }

  public int getId() {
  		return id;
  }

  public void setId(int id) {
  		this.id = id;
    }

  }
  		
  